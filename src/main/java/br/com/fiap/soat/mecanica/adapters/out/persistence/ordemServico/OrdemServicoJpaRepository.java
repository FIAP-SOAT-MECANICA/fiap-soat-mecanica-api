package br.com.fiap.soat.mecanica.adapters.out.persistence.ordemServico;

import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import br.com.fiap.soat.mecanica.domain.enums.StatusRecursoEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrdemServicoJpaRepository extends JpaRepository<OrdemServicoEntity, UUID> {

    List<OrdemServicoEntity> findAllByVeiculoId(UUID veiculoId);

    @Query(
            value = """
                    SELECT os
                    FROM OrdemServicoEntity os
                    WHERE os.status = :statusAtivo
                      AND os.situacao IN (:emExecucao, :aguardandoAprovacao, :emDiagnostico, :recebida)
                    ORDER BY CASE
                        WHEN os.situacao = :emExecucao THEN 1
                        WHEN os.situacao = :aguardandoAprovacao THEN 2
                        WHEN os.situacao = :emDiagnostico THEN 3
                        WHEN os.situacao = :recebida THEN 4
                        ELSE 5
                    END,
                    os.dataRecebida ASC,
                    os.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(os)
                    FROM OrdemServicoEntity os
                    WHERE os.status = :statusAtivo
                      AND os.situacao IN (:emExecucao, :aguardandoAprovacao, :emDiagnostico, :recebida)
                    """
    )
    Page<OrdemServicoEntity> listarAtivasOrdenadas(
            @Param("statusAtivo") StatusRecursoEnum statusAtivo,
            @Param("emExecucao") SituacaoOrdemServicoEnum emExecucao,
            @Param("aguardandoAprovacao") SituacaoOrdemServicoEnum aguardandoAprovacao,
            @Param("emDiagnostico") SituacaoOrdemServicoEnum emDiagnostico,
            @Param("recebida") SituacaoOrdemServicoEnum recebida,
            Pageable pageable
    );
}
