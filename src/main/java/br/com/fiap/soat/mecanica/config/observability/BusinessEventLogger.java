package br.com.fiap.soat.mecanica.config.observability;

import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BusinessEventLogger {

    public static void ordemServicoCriada(UUID ordemServicoId, UUID veiculoId, UUID mecanicoId) {
        log.atInfo()
                .addKeyValue("event", "order_service_created")
                .addKeyValue("orderServiceId", ordemServicoId)
                .addKeyValue("vehicleId", veiculoId)
                .addKeyValue("mechanicId", mecanicoId)
                .log("Order service created");
    }

    public static void situacaoOrdemServicoAlterada(
            UUID ordemServicoId,
            SituacaoOrdemServicoEnum situacaoAnterior,
            SituacaoOrdemServicoEnum novaSituacao) {
        log.atInfo()
                .addKeyValue("event", "order_service_status_changed")
                .addKeyValue("orderServiceId", ordemServicoId)
                .addKeyValue("previousStatus", situacaoAnterior)
                .addKeyValue("newStatus", novaSituacao)
                .log("Order service status changed");
    }

    public static void ordemServicoCancelada(UUID ordemServicoId) {
        log.atInfo()
                .addKeyValue("event", "order_service_cancelled")
                .addKeyValue("orderServiceId", ordemServicoId)
                .addKeyValue("previousResourceStatus", "ATIVO")
                .addKeyValue("newResourceStatus", "INATIVO")
                .log("Order service cancelled");
    }
}
