package br.com.fiap.soat.mecanica.application.ordemServico.usecase;

import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecusarOrcamentoUseCaseTest {

    @Mock
    private OrdemServicoRepository ordemServicoRepository;

    @Mock
    private NotificarAlteracaoSituacaoOrdemServicoUseCase notificarAlteracaoSituacaoOrdemServicoUseCase;

    @InjectMocks
    private RecusarOrcamentoUseCase useCase;

    @Test
    @DisplayName("Deve recusar orçamento e voltar OS para EM_DIAGNOSTICO")
    void deveRecusar_quandoOsAguardandoAprovacao() {
        OrdemServico os = TestDataFactory.criarOrdemServicoAguardandoAprovacao();
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(ordemServicoRepository.salvar(any())).thenReturn(os);

        OrdemServico resultado = useCase.executar(os.getId());

        assertThat(resultado.getSituacao()).isEqualTo(SituacaoOrdemServicoEnum.EM_DIAGNOSTICO);
        verify(notificarAlteracaoSituacaoOrdemServicoUseCase)
                .executar(resultado, SituacaoOrdemServicoEnum.AGUARDANDO_APROVACAO);
    }

    @Test
    @DisplayName("Não deve notificar quando a persistência falhar")
    void naoDeveNotificar_quandoPersistenciaFalhar() {
        OrdemServico os = TestDataFactory.criarOrdemServicoAguardandoAprovacao();
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(ordemServicoRepository.salvar(any())).thenThrow(new RuntimeException("falha simulada"));

        assertThatThrownBy(() -> useCase.executar(os.getId()))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(notificarAlteracaoSituacaoOrdemServicoUseCase);
    }

    @Test
    @DisplayName("Deve lançar exceção quando OS não encontrada")
    void deveLancarExcecao_quandoNaoEncontrada() {
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando OS não está aguardando aprovação")
    void deveLancarExcecao_quandoOsNaoAguardandoAprovacao() {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));

        assertThatThrownBy(() -> useCase.executar(os.getId()))
                .isInstanceOf(RegraNegocioException.class);
    }
}
