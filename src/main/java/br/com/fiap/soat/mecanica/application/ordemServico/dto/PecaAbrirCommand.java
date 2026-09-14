package br.com.fiap.soat.mecanica.application.ordemServico.dto;

import java.util.UUID;

public record PecaAbrirCommand(UUID pecaId, Integer quantidade) {
}
