package br.com.fiap.soat.mecanica.application.prestacaoServico;

import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import br.com.fiap.soat.mecanica.application.ordemServico.usecase.NotificarAlteracaoSituacaoOrdemServicoUseCase;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.domain.prestacaoServico.PrestacaoServico;
import br.com.fiap.soat.mecanica.domain.prestacaoServico.PrestacaoServicoRepository;
import br.com.fiap.soat.mecanica.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinalizarPrestacaoServicoUseCaseTest {

    @Mock
    private PrestacaoServicoRepository prestacaoServicoRepository;
    @Mock
    private OrdemServicoRepository ordemServicoRepository;
    @Mock
    private NotificarAlteracaoSituacaoOrdemServicoUseCase notificarAlteracaoSituacaoOrdemServicoUseCase;
    @InjectMocks
    private FinalizarPrestacaoServicoUseCase useCase;

    @Test
    @DisplayName("Deve finalizar prestação sem finalizar OS (ainda há prestações não finalizadas)")
    void deveFinalizar_quandoAindaHaPrestacoesNaoFinalizadas() {
        // Arrange
        PrestacaoServico ps = TestDataFactory.criarPrestacaoServicoIniciada();
        PrestacaoServico outraPs = TestDataFactory.criarPrestacaoServicoValida();
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();
        when(prestacaoServicoRepository.buscarPorId(any())).thenReturn(Optional.of(ps));
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(prestacaoServicoRepository.salvar(any())).thenReturn(ps);
        when(prestacaoServicoRepository.buscarTodosPorOrdemServicoId(any())).thenReturn(List.of(ps, outraPs));

        // Act
        PrestacaoServico resultado = useCase.executar(ps.getId());

        // Assert
        assertThat(resultado).isNotNull();
        verify(ordemServicoRepository, never()).salvar(any());
        verifyNoInteractions(notificarAlteracaoSituacaoOrdemServicoUseCase);
    }

    @Test
    @DisplayName("Deve finalizar prestação e OS quando todas prestações finalizadas")
    void deveFinalizar_quandoTodasPrestacoesFinalizadas() {
        // Arrange
        PrestacaoServico ps = TestDataFactory.criarPrestacaoServicoIniciada();
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();
        when(prestacaoServicoRepository.buscarPorId(any())).thenReturn(Optional.of(ps));
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(prestacaoServicoRepository.salvar(any())).thenReturn(ps);

        PrestacaoServico finalizada = TestDataFactory.criarPrestacaoServicoFinalizada();
        when(prestacaoServicoRepository.buscarTodosPorOrdemServicoId(any())).thenReturn(List.of(finalizada));
        when(ordemServicoRepository.salvar(any())).thenReturn(os);

        // Act
        PrestacaoServico resultado = useCase.executar(ps.getId());

        // Assert
        assertThat(resultado).isNotNull();
        verify(ordemServicoRepository).salvar(any());
        verify(notificarAlteracaoSituacaoOrdemServicoUseCase)
                .executar(os, SituacaoOrdemServicoEnum.EM_EXECUCAO);
    }

    @Test
    @DisplayName("Deve lançar exceção quando OS não em execução")
    void deveLancarExcecao_quandoOsNaoEmExecucao() {
        PrestacaoServico ps = TestDataFactory.criarPrestacaoServicoIniciada();
        OrdemServico os = TestDataFactory.criarOrdemServicoAguardandoAprovacao();
        when(prestacaoServicoRepository.buscarPorId(any())).thenReturn(Optional.of(ps));
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        assertThatThrownBy(() -> useCase.executar(ps.getId()))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando prestação não encontrada")
    void deveLancarExcecao_quandoNaoEncontrada() {
        when(prestacaoServicoRepository.buscarPorId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
