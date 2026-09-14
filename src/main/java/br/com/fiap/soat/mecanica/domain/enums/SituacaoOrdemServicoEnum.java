package br.com.fiap.soat.mecanica.domain.enums;

public enum SituacaoOrdemServicoEnum {
    RECEBIDA("Recebida"),
    EM_DIAGNOSTICO("Em diagnóstico"),
    AGUARDANDO_APROVACAO("Aguardando aprovação"),
    EM_EXECUCAO("Em execução"),
    FINALIZADA("Finalizada"),
    ENTREGUE("Entregue");

    private final String descricao;

    SituacaoOrdemServicoEnum(String descricao) {
        this.descricao = descricao;
    }

    @Override
    public String toString() {
        return descricao;
    }
}
