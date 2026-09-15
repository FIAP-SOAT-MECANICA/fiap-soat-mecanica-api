package br.com.fiap.soat.mecanica.application.ordemServico.usecase;

import br.com.fiap.soat.mecanica.application.ordemServico.NotificacaoOrdemServicoPort;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.NotificacaoOrdemServico;
import br.com.fiap.soat.mecanica.domain.cliente.Cliente;
import br.com.fiap.soat.mecanica.domain.cliente.ClienteRepository;
import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.veiculo.Veiculo;
import br.com.fiap.soat.mecanica.domain.veiculo.VeiculoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static br.com.fiap.soat.mecanica.config.observability.BusinessEventLogger.situacaoOrdemServicoAlterada;

@Slf4j
@Service
public class NotificarAlteracaoSituacaoOrdemServicoUseCase {

    private final VeiculoRepository veiculoRepository;
    private final ClienteRepository clienteRepository;
    private final NotificacaoOrdemServicoPort notificacaoOrdemServicoPort;
    private final boolean notificacaoHabilitada;

    public NotificarAlteracaoSituacaoOrdemServicoUseCase(
            VeiculoRepository veiculoRepository,
            ClienteRepository clienteRepository,
            NotificacaoOrdemServicoPort notificacaoOrdemServicoPort,
            @Value("${app.notification.email.enabled:true}") boolean notificacaoHabilitada) {
        this.veiculoRepository = veiculoRepository;
        this.clienteRepository = clienteRepository;
        this.notificacaoOrdemServicoPort = notificacaoOrdemServicoPort;
        this.notificacaoHabilitada = notificacaoHabilitada;
    }

    public void executar(OrdemServico ordemServico, SituacaoOrdemServicoEnum situacaoAnterior) {
        if (situacaoAnterior == ordemServico.getSituacao()) {
            return;
        }

        executarAposCommit(() -> situacaoOrdemServicoAlterada(
                ordemServico.getId(), situacaoAnterior, ordemServico.getSituacao()));

        if (!notificacaoHabilitada) {
            return;
        }

        try {
            Veiculo veiculo = veiculoRepository.buscarPorId(ordemServico.getVeiculoId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Veiculo da ordem de servico nao encontrado"));
            Cliente cliente = clienteRepository.buscarPorId(veiculo.getClienteId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente da ordem de servico nao encontrado"));

            NotificacaoOrdemServico notificacao = new NotificacaoOrdemServico(
                    ordemServico.getId(),
                    cliente.getNome(),
                    cliente.getEmail().getValue(),
                    situacaoAnterior,
                    ordemServico.getSituacao()
            );

            executarAposCommit(() -> enviar(notificacao));
        } catch (RuntimeException ex) {
            registrarFalha(ordemServico.getId(), ex);
        }
    }

    private void executarAposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
            return;
        }

        acao.run();
    }

    private void enviar(NotificacaoOrdemServico notificacao) {
        try {
            notificacaoOrdemServicoPort.enviar(notificacao);
            log.atInfo()
                    .addKeyValue("event", "order_service_notification_sent")
                    .addKeyValue("orderServiceId", notificacao.ordemServicoId())
                    .addKeyValue("previousStatus", notificacao.situacaoAnterior())
                    .addKeyValue("newStatus", notificacao.novaSituacao())
                    .log("Order service notification sent");
        } catch (RuntimeException ex) {
            registrarFalha(notificacao, ex);
        }
    }

    private void registrarFalha(java.util.UUID ordemServicoId, RuntimeException ex) {
        log.atError()
                .setCause(ex)
                .addKeyValue("event", "order_service_notification_preparation_failed")
                .addKeyValue("orderServiceId", ordemServicoId)
                .addKeyValue("exceptionType", ex.getClass().getSimpleName())
                .log("Order service notification preparation failed");
    }

    private void registrarFalha(NotificacaoOrdemServico notificacao, RuntimeException ex) {
        log.atError()
                .setCause(ex)
                .addKeyValue("event", "order_service_notification_send_failed")
                .addKeyValue("orderServiceId", notificacao.ordemServicoId())
                .addKeyValue("previousStatus", notificacao.situacaoAnterior())
                .addKeyValue("newStatus", notificacao.novaSituacao())
                .addKeyValue("exceptionType", ex.getClass().getSimpleName())
                .log("Order service notification send failed");
    }
}
