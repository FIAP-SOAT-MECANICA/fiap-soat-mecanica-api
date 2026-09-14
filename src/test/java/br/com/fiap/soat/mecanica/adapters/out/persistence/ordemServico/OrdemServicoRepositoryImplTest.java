package br.com.fiap.soat.mecanica.adapters.out.persistence.ordemServico;

import br.com.fiap.soat.mecanica.adapters.out.persistence.ordemServico.mapper.OrdemServicoMapper;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.Pagina;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.Paginacao;
import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.support.DatabaseTestFixtures;
import br.com.fiap.soat.mecanica.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({OrdemServicoRepositoryImpl.class, OrdemServicoMapper.class})
class OrdemServicoRepositoryImplTest extends PostgresIntegrationTest {

    @Autowired
    private OrdemServicoRepositoryImpl repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID veiculoId;
    private UUID usuarioId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE alocacao_pecas, prestacao_servicos, ordem_servicos,
                    veiculos, clientes, pecas, servicos, usuarios CASCADE
                """);
        DatabaseTestFixtures.OrdemServicoDependencies dependencies =
                DatabaseTestFixtures.insertOrdemServicoDependencies(jdbcTemplate);
        veiculoId = dependencies.veiculoId();
        usuarioId = dependencies.usuarioId();
    }

    @Test
    @DisplayName("Deve salvar e buscar OS por ID")
    void deveSalvarEBuscarPorId() {
        OrdemServico os = new OrdemServico("Observacao teste", veiculoId, usuarioId);

        OrdemServico salva = repository.salvar(os);
        Optional<OrdemServico> encontrada = repository.buscarPorId(salva.getId());

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getObservacao()).isEqualTo("Observacao teste");
    }

    @Test
    @DisplayName("Deve buscar todas OS por veículo ID")
    void deveBuscarTodosPorVeiculoId() {
        repository.salvar(new OrdemServico("OS 1", veiculoId, usuarioId));
        repository.salvar(new OrdemServico("OS 2", veiculoId, usuarioId));

        List<OrdemServico> lista = repository.buscarTodosPorVeiculoId(veiculoId);

        assertThat(lista).hasSize(2);
    }

    @Test
    @DisplayName("Deve listar somente OS ativas na prioridade e ordem cronologica definidas")
    void deveListarAtivasOrdenadas() {
        LocalDateTime dataBase = LocalDateTime.of(2026, 1, 1, 8, 0);

        UUID execucaoAntiga = uuid(1);
        UUID execucaoMesmoHorarioIdMenor = uuid(2);
        UUID execucaoMesmoHorarioIdMaior = uuid(3);
        UUID aguardando = uuid(4);
        UUID diagnostico = uuid(5);
        UUID recebida = uuid(6);
        UUID finalizada = uuid(7);
        UUID entregue = uuid(8);
        UUID inativa = uuid(9);

        inserirOrdem(execucaoAntiga, "ATIVO", "EM_EXECUCAO", dataBase);
        inserirOrdem(execucaoMesmoHorarioIdMaior, "ATIVO", "EM_EXECUCAO", dataBase.plusDays(1));
        inserirOrdem(execucaoMesmoHorarioIdMenor, "ATIVO", "EM_EXECUCAO", dataBase.plusDays(1));
        inserirOrdem(aguardando, "ATIVO", "AGUARDANDO_APROVACAO", dataBase.minusDays(10));
        inserirOrdem(diagnostico, "ATIVO", "EM_DIAGNOSTICO", dataBase.minusDays(20));
        inserirOrdem(recebida, "ATIVO", "RECEBIDA", dataBase.minusDays(30));
        inserirOrdem(finalizada, "ATIVO", "FINALIZADA", dataBase.minusDays(40));
        inserirOrdem(entregue, "ATIVO", "ENTREGUE", dataBase.minusDays(50));
        inserirOrdem(inativa, "INATIVO", "EM_EXECUCAO", dataBase.minusDays(60));

        Pagina<OrdemServico> pagina = repository.listarAtivas(new Paginacao(0, 20));

        assertThat(pagina.content())
                .extracting(OrdemServico::getId)
                .containsExactly(
                        execucaoAntiga,
                        execucaoMesmoHorarioIdMenor,
                        execucaoMesmoHorarioIdMaior,
                        aguardando,
                        diagnostico,
                        recebida
                );
        assertThat(pagina.content())
                .extracting(OrdemServico::getSituacao)
                .containsExactly(
                        SituacaoOrdemServicoEnum.EM_EXECUCAO,
                        SituacaoOrdemServicoEnum.EM_EXECUCAO,
                        SituacaoOrdemServicoEnum.EM_EXECUCAO,
                        SituacaoOrdemServicoEnum.AGUARDANDO_APROVACAO,
                        SituacaoOrdemServicoEnum.EM_DIAGNOSTICO,
                        SituacaoOrdemServicoEnum.RECEBIDA
                );
        assertThat(repository.buscarPorId(finalizada)).isPresent();
        assertThat(repository.buscarPorId(entregue)).isPresent();
        assertThat(repository.buscarPorId(inativa)).isPresent();
    }

    @Test
    @DisplayName("Deve paginar a listagem e retornar pagina vazia")
    void devePaginarERetornarPaginaVazia() {
        inserirOrdem(uuid(1), "ATIVO", "EM_EXECUCAO", LocalDateTime.of(2026, 1, 1, 8, 0));
        inserirOrdem(uuid(2), "ATIVO", "AGUARDANDO_APROVACAO", LocalDateTime.of(2026, 1, 1, 8, 0));
        inserirOrdem(uuid(3), "ATIVO", "RECEBIDA", LocalDateTime.of(2026, 1, 1, 8, 0));

        Pagina<OrdemServico> primeira = repository.listarAtivas(new Paginacao(0, 2));
        Pagina<OrdemServico> segunda = repository.listarAtivas(new Paginacao(1, 2));
        Pagina<OrdemServico> vazia = repository.listarAtivas(new Paginacao(2, 2));

        assertThat(primeira.content()).hasSize(2);
        assertThat(primeira.totalElements()).isEqualTo(3);
        assertThat(primeira.totalPages()).isEqualTo(2);
        assertThat(primeira.first()).isTrue();
        assertThat(primeira.last()).isFalse();

        assertThat(segunda.content()).hasSize(1);
        assertThat(segunda.first()).isFalse();
        assertThat(segunda.last()).isTrue();
        assertThat(primeira.content()).doesNotContainAnyElementsOf(segunda.content());

        assertThat(vazia.content()).isEmpty();
        assertThat(vazia.totalElements()).isEqualTo(3);
    }

    private void inserirOrdem(UUID id, String status, String situacao, LocalDateTime dataRecebida) {
        jdbcTemplate.update("""
                INSERT INTO ordem_servicos (
                    id, status, situacao, data_recebida, pago, valor_total, observacao, veiculo_id, usuario_id
                )
                VALUES (?, ?, ?, ?, FALSE, ?, 'OS listagem', ?, ?)
                """,
                id,
                status,
                situacao,
                dataRecebida,
                BigDecimal.ZERO,
                veiculoId,
                usuarioId);
    }

    private UUID uuid(int valor) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(valor));
    }
}
