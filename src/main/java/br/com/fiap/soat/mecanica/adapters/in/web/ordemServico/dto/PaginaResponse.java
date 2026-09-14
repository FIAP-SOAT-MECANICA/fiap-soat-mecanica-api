package br.com.fiap.soat.mecanica.adapters.in.web.ordemServico.dto;

import java.util.List;

public record PaginaResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
