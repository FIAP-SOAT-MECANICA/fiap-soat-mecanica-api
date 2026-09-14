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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class VoltarOrdemServicoParaDiagnosticoUseCaseTest {

    @Mock
    private OrdemServicoRepository ordemServicoRepository;
    @Mock
    private NotificarAlteracaoSituacaoOrdemServicoUseCase notificarAlteracaoSituacaoOrdemServicoUseCase;
    @InjectMocks
    private VoltarOrdemServicoParaDiagnosticoUseCase useCase;

    @Test
    @DisplayName("Deve voltar OS para diagnóstico com sucesso")
    void deveVoltar_quandoOsAguardando() {
        // Arrange
        OrdemServico os = TestDataFactory.criarOrdemServicoAguardandoAprovacao();
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(ordemServicoRepository.salvar(any())).thenReturn(os);

        // Act
        OrdemServico resultado = useCase.executar(os.getId());

        // Assert
        assertThat(resultado).isNotNull();
        verify(notificarAlteracaoSituacaoOrdemServicoUseCase)
                .executar(resultado, SituacaoOrdemServicoEnum.AGUARDANDO_APROVACAO);
    }

    @Test
    @DisplayName("Deve lançar exceção quando OS não encontrada")
    void deveLancarExcecao_quandoNaoEncontrada() {
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(notificarAlteracaoSituacaoOrdemServicoUseCase, never()).executar(any(), any());
    }
}
