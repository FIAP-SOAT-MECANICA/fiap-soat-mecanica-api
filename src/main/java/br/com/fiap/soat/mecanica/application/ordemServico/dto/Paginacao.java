package br.com.fiap.soat.mecanica.application.ordemServico.dto;

public record Paginacao(int page, int size) {

    private static final int TAMANHO_MAXIMO = 100;

    public Paginacao {
        if (page < 0) {
            throw new IllegalArgumentException("A pagina deve ser maior ou igual a zero");
        }
        if (size < 1 || size > TAMANHO_MAXIMO) {
            throw new IllegalArgumentException("O tamanho da pagina deve estar entre 1 e 100");
        }
    }
}
