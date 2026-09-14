package br.com.fiap.soat.mecanica.adapters.in.web.ordemServico.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ServicoAbrirRequest(

        @NotNull(message = "O serviço é obrigatório")
        UUID servicoId,

        @NotNull(message = "O preço de mão de obra é obrigatório")
        @Positive(message = "O preço de mão de obra deve ser maior que zero")
        BigDecimal precoMaoDeObra,

        @Valid
        List<PecaAbrirRequest> pecas
) {
}
