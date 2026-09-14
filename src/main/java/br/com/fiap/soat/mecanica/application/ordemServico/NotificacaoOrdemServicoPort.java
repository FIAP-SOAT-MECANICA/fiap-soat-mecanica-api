package br.com.fiap.soat.mecanica.application.ordemServico;

import br.com.fiap.soat.mecanica.application.ordemServico.dto.NotificacaoOrdemServico;

public interface NotificacaoOrdemServicoPort {

    void enviar(NotificacaoOrdemServico notificacao);
}
