package br.ufsm.poli.csi.redes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Mensagem {

    public enum TipoMensagem {sonda, msg_individual, fim_chat, msg_grupo}
    private TipoMensagem tipoMensagem;

    private String usuario;
    private String status;
    private String mensagem;
}
