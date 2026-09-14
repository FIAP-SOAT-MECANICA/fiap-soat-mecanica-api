package br.com.fiap.soat.mecanica.application.ordemServico.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ServicoAbrirCommand(UUID servicoId, BigDecimal precoMaoDeObra, List<PecaAbrirCommand> pecas) {
}
