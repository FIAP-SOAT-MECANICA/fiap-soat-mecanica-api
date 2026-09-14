package br.com.fiap.soat.mecanica.application.ordemServico.usecase;

import br.com.fiap.soat.mecanica.domain.alocacaoPecas.AlocacaoPeca;
import br.com.fiap.soat.mecanica.domain.alocacaoPecas.AlocacaoPecaRepository;
import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.domain.peca.Peca;
import br.com.fiap.soat.mecanica.domain.peca.PecaRepository;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelarOrdemServicoPorDesistenciaUseCaseTest {

    @Mock
    private OrdemServicoRepository ordemServicoRepository;
    @Mock
    private PrestacaoServicoRepository prestacaoServicoRepository;
    @Mock
    private AlocacaoPecaRepository alocacaoPecaRepository;
    @Mock
    private PecaRepository pecaRepository;
    @Mock
    private NotificarAlteracaoSituacaoOrdemServicoUseCase notificacaoUseCase;
    @InjectMocks
    private CancelarOrdemServicoPorDesistenciaUseCase useCase;

    @Test
    @DisplayName("Deve cancelar OS por desistência com sucesso")
    void deveCancelar_quandoOsAguardandoAprovacao() {
        // Arrange
        OrdemServico os = TestDataFactory.criarOrdemServicoAguardandoAprovacao();
        PrestacaoServico ps = TestDataFactory.criarPrestacaoServicoValida();
        AlocacaoPeca al = TestDataFactory.criarAlocacaoPecaValida();
        Peca peca = TestDataFactory.criarPecaValida();
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(prestacaoServicoRepository.buscarTodosPorOrdemServicoId(any())).thenReturn(List.of(ps));
        when(alocacaoPecaRepository.buscarTodosPorPrestacaoServicoId(any())).thenReturn(List.of(al));
        when(pecaRepository.buscarPorId(any())).thenReturn(Optional.of(peca));
        when(ordemServicoRepository.salvar(any())).thenReturn(os);

        // Act
        OrdemServico resultado = useCase.executar(os.getId());

        // Assert
        assertThat(resultado).isNotNull();
        verifyNoInteractions(notificacaoUseCase);
    }

    @Test
    @DisplayName("Deve lançar exceção quando OS não encontrada")
    void deveLancarExcecao_quandoNaoEncontrada() {
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.executar(UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
