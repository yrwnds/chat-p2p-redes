package br.ufsm.poli.csi.redes.service;

import br.ufsm.poli.csi.redes.model.Mensagem;
import br.ufsm.poli.csi.redes.model.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

public class UDPServiceImpl implements UDPService {

    private Usuario usuario = null;

    ArrayList<Usuario> usuariosOnline = new ArrayList<>();

    public UDPServiceImpl() {
        init();
    }

    @SneakyThrows
    private void init() {
        new Thread(new Server()).start();
        new Thread(new EnviaSonda()).start();
        new Thread(new ChecaAtividade()).start();
//        Runtime.getRuntime().addShutdownHook(new Thread(this::fimChat));
    }

    public class ChecaAtividade implements Runnable {
        @SneakyThrows
        @Override
        public void run() {
            while (true) {
                Thread.sleep(10000);

                System.out.println("Checando atividade.");

                Iterator<Usuario> iterator = usuariosOnline.iterator();
                while (iterator.hasNext()) {
                    Usuario usuario = iterator.next();
                    if (System.currentTimeMillis() - usuario.getLastseen() > 30000) {
                        iterator.remove();
                        usuarioListener.usuarioRemovido(usuario);

                        System.out.println("[SERVER] Excluiu usuario " + usuario.getNome() + " por inatividade.");
                    }
                }
            }
        }
    }


    public class Server implements Runnable {
        @SneakyThrows
        @Override
        public void run() {

            DatagramSocket socket = new DatagramSocket(8080);

            while (usuarioListener == null) {
                Thread.sleep(1000);
            }

            while (true) {
                DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
                socket.receive(packet);

                String strMsg = new String(packet.getData(), 0, packet.getLength());

                ObjectMapper mapper = new ObjectMapper();

                try{
                    Mensagem msg = mapper.readValue(strMsg, Mensagem.class);

                    Usuario remetente = null;
                    boolean existe = false;
                    for(int i = 0 ; i < usuariosOnline.size() ; i ++ ) {
                        if(usuariosOnline.get(i).getEndereco().equals(packet.getAddress().getHostAddress())){
                            remetente = usuariosOnline.get(i);
                            existe = true;
                        }
                    }
                    if(!existe) {
                        remetente = new Usuario();
                        remetente.setEndereco(InetAddress.getByName(packet.getAddress().getHostAddress()));
                        remetente.setNome(msg.getUsuario());
                        remetente.setStatus(Usuario.StatusUsuario.valueOf(msg.getStatus()));
                        remetente.setLastseen(System.currentTimeMillis());
                        usuariosOnline.add(remetente);
                    }


                    String msgRecebida = msg.getMsg();

                    if (msg.getTipoMensagem() == Mensagem.TipoMensagem.msg_grupo) {
                        System.out.println("[SERVER] Mensagem recebida no chat geral de: " + packet.getAddress().getHostAddress() + " (" + remetente.getNome() + ")");
                        if (!packet.getAddress().equals(usuario.getEndereco())) {
                            mensagemListener.mensagemRecebida(msgRecebida, remetente, true);
                        }

                    } else if (msg.getTipoMensagem() == Mensagem.TipoMensagem.msg_individual) {
                        System.out.println("[SERVER] Mensagem recebida individual de: " + packet.getAddress().getHostAddress() + " (" + remetente.getNome() + ")");
                        if (!packet.getAddress().equals(usuario.getEndereco())) {
                            mensagemListener.mensagemRecebida(msgRecebida, remetente, false);
                        }

                    } else if (msg.getTipoMensagem() == Mensagem.TipoMensagem.sonda) {
                        System.out.println("[SERVER] Sonda detectada de: " + packet.getAddress().getHostAddress() + " (" + remetente.getNome() + ")");

                        boolean jaexiste = false;
                        for (int i = 0; i < usuariosOnline.size(); i++) {
                            if (usuariosOnline.get(i).getEndereco().equals(remetente.getEndereco())) {

                                usuariosOnline.get(i).setLastseen(remetente.getLastseen());
                                usuariosOnline.get(i).setStatus(remetente.getStatus());

                                usuarioListener.usuarioAlterado(remetente);

                                jaexiste = true;
                            }
                        }
                        if (jaexiste == false) {
                            usuariosOnline.add(remetente);
                        }

                        usuarioListener.usuarioAdicionado(remetente);

                    } else if (msg.getTipoMensagem() == Mensagem.TipoMensagem.fim_chat) {
                        System.out.println("[SERVER] fim_chat detectado de: " + packet.getAddress().getHostAddress() + " (" + remetente.getNome() + ")");

                        usuarioListener.usuarioRemovido(remetente);

                    } else{
                        System.out.println("ERRO DE MENSAGEM de: " + packet.getAddress().getHostAddress() + " (" + remetente.getNome() + ")");
                        System.out.println("\n" + strMsg);
                    }
                } catch(Exception e){
                    e.printStackTrace();
                }

            }
        }
    }

    @SneakyThrows
    @Override
    public void fimChat(Usuario usuario) {
        Mensagem mensagem = new Mensagem();
        mensagem.setTipoMensagem(Mensagem.TipoMensagem.fim_chat);

        mensagem.setUsuario(usuario.getNome());
        mensagem.setStatus(usuario.getStatus().toString());
        mensagem.setMsg("Chat encerrado com este usuario porque o mesmo foi desconectado.");

        for (int i = 0 ; i < 255 ; i ++){
            ObjectMapper mapper = new ObjectMapper();
            String strMensagem = mapper.writeValueAsString(mensagem);
            byte[] bMensagem = strMensagem.getBytes();

            DatagramPacket packet = new DatagramPacket(bMensagem, bMensagem.length, InetAddress.getByName("192.168.83." + i), 8080);
            DatagramSocket socket = new DatagramSocket();
            socket.send(packet);
        }
        System.out.println("[CLIENT] Chat encerrado.");
    }

    private class EnviaSonda implements Runnable {
        @SneakyThrows
        @Override
        public void run() {
            while (true) {
                Thread.sleep(5000);
                if (usuario == null) {
                    continue;
                }

                Mensagem mensagem = new Mensagem();
                mensagem.setTipoMensagem(Mensagem.TipoMensagem.sonda);

                mensagem.setUsuario(usuario.getNome());
                mensagem.setStatus(usuario.getStatus().toString());


                ObjectMapper mapper = new ObjectMapper();
                String strMensagem = mapper.writeValueAsString(mensagem);
                byte[] bMensagem = strMensagem.getBytes();

                for(int i = 0 ; i < 255 ; i ++){
                    DatagramPacket packet = new DatagramPacket(bMensagem,
                            bMensagem.length,
                            InetAddress.getByName("192.168.83." +i),
                            8080);
                    DatagramSocket socket = new DatagramSocket();
                    socket.send(packet);
                }

                System.out.println("[CLIENT] Enviando sonda...");

                long lastseen = System.currentTimeMillis();

                usuario.setLastseen(lastseen);
            }
        }
    }


    @Override
    @SneakyThrows
    public void enviarMensagem(String mensagem, Usuario destinatario, boolean chatGeral) {

        DatagramSocket socket = new DatagramSocket();

        Mensagem newMsg = new Mensagem();
        newMsg.setMsg(mensagem);
        newMsg.setUsuario(usuario.getNome());
        newMsg.setStatus(usuario.getStatus().toString());

        InetAddress address;

        ObjectMapper mapper = new ObjectMapper();

        if (chatGeral) {
            newMsg.setTipoMensagem(Mensagem.TipoMensagem.msg_grupo);
            String strMsg = mapper.writeValueAsString(newMsg);
            byte[] bMsg = strMsg.getBytes();
            for(int i = 0 ; i < 255 ; i ++){
                    address = InetAddress.getByName("192.168.83." + i);

                    DatagramPacket packet = new DatagramPacket(bMsg, bMsg.length, address, 8080);
                    socket.send(packet);
            }
        } else {
            newMsg.setTipoMensagem(Mensagem.TipoMensagem.msg_individual);
            String strMsg = mapper.writeValueAsString(newMsg);
            byte[] bMsg = strMsg.getBytes();
            address = destinatario.getEndereco();

            DatagramPacket packet = new DatagramPacket(bMsg, bMsg.length, address, 8080);
            socket.send(packet);
        }
    }


    @Override
    public void usuarioAlterado(Usuario usuario) {
        this.usuario = usuario;
    }

    private UDPServiceMensagemListener mensagemListener = null;

    @Override
    public void addListenerMensagem(UDPServiceMensagemListener listener) {
        this.mensagemListener = listener;
    }


    private UDPServiceUsuarioListener usuarioListener = null;

    @Override
    public void addListenerUsuario(UDPServiceUsuarioListener listener) {
        this.usuarioListener = listener;
    }
}
