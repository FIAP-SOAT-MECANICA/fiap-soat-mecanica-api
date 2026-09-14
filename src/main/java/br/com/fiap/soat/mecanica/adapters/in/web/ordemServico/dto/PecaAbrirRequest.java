package br.com.fiap.soat.mecanica.adapters.in.web.ordemServico.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PecaAbrirRequest(

        @NotNull(message = "A peça é obrigatória")
        UUID pecaId,

        @NotNull(message = "A quantidade é obrigatória")
        @Min(value = 1, message = "A quantidade deve ser maior que zero")
        Integer quantidade
) {
}
