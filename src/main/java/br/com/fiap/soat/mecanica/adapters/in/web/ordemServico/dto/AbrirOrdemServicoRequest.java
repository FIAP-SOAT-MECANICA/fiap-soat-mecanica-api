package br.com.fiap.soat.mecanica.adapters.in.web.ordemServico.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record AbrirOrdemServicoRequest(

        String observacao,

        @NotNull(message = "O veículo é obrigatório")
        UUID veiculoId,

        @NotNull(message = "Pelo menos um serviço é obrigatório")
        @NotEmpty(message = "Pelo menos um serviço é obrigatório")
        @Valid
        List<ServicoAbrirRequest> servicos
) {
}
