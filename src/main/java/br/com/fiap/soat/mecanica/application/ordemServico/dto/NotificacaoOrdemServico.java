package br.com.fiap.soat.mecanica.application.ordemServico.dto;

import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;

import java.util.UUID;

public record NotificacaoOrdemServico(
        UUID ordemServicoId,
        String nomeCliente,
        String emailCliente,
        SituacaoOrdemServicoEnum situacaoAnterior,
        SituacaoOrdemServicoEnum novaSituacao
) {
}
