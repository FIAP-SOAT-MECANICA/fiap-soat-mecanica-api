package br.com.fiap.soat.mecanica.application.ordemServico.usecase;

import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnviarOrdemServicoParaAprovacaoUseCaseTest {

    @Mock
    private OrdemServicoRepository ordemServicoRepository;
    @Mock
    private NotificarAlteracaoSituacaoOrdemServicoUseCase notificarAlteracaoSituacaoOrdemServicoUseCase;
    @InjectMocks
    private EnviarOrdemServicoParaAprovacaoUseCase useCase;

    @Test
    @DisplayName("Deve enviar OS para aprovação com sucesso")
    void deveEnviar_quandoOsEmDiagnostico() {
        // Arrange
        OrdemServico os = TestDataFactory.criarOrdemServicoEmDiagnostico();
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(ordemServicoRepository.salvar(any())).thenReturn(os);

        // Act
        OrdemServico resultado = useCase.executar(os.getId());

        // Assert
        assertThat(resultado).isNotNull();
        verify(notificarAlteracaoSituacaoOrdemServicoUseCase)
                .executar(resultado, SituacaoOrdemServicoEnum.EM_DIAGNOSTICO);
    }

    @Test
    @DisplayName("Deve lançar exceção quando OS não encontrada")
    void deveLancarExcecao_quandoNaoEncontrada() {
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(notificarAlteracaoSituacaoOrdemServicoUseCase, never()).executar(any(), any());
    }

    @Test
    @DisplayName("Nao deve notificar quando a persistencia falhar")
    void naoDeveNotificar_quandoPersistenciaFalhar() {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmDiagnostico();
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(ordemServicoRepository.salvar(any())).thenThrow(new IllegalStateException("falha simulada"));

        assertThatThrownBy(() -> useCase.executar(os.getId()))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(notificarAlteracaoSituacaoOrdemServicoUseCase);
    }
}
