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
        log.info("order_service_created orderServiceId={} vehicleId={} mechanicId={}",
                ordemServicoId, veiculoId, mecanicoId);
    }

    public static void situacaoOrdemServicoAlterada(
            UUID ordemServicoId,
            SituacaoOrdemServicoEnum situacaoAnterior,
            SituacaoOrdemServicoEnum novaSituacao) {
        log.info("order_service_status_changed orderServiceId={} previousStatus={} newStatus={}",
                ordemServicoId, situacaoAnterior, novaSituacao);
    }
}
