package br.com.fiap.soat.mecanica.application.ordemServico.usecase;

import br.com.fiap.soat.mecanica.application.alocacaoPeca.CadastrarAlocacaoPecaUseCase;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.PecaAbrirCommand;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.ServicoAbrirCommand;
import br.com.fiap.soat.mecanica.application.prestacaoServico.CadastrarPrestacaoServicoUseCase;
import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbrirOrdemServicoUseCaseTest {

    @Mock
    private CadastrarOrdemServicoUseCase cadastrarOrdemServicoUseCase;
    @Mock
    private CadastrarPrestacaoServicoUseCase cadastrarPrestacaoServicoUseCase;
    @Mock
    private CadastrarAlocacaoPecaUseCase cadastrarAlocacaoPecaUseCase;
    @Mock
    private OrdemServicoRepository ordemServicoRepository;

    @InjectMocks
    private AbrirOrdemServicoUseCase useCase;

    @Test
    @DisplayName("Deve abrir OS com serviços e peças com sucesso")
    void deveAbrir_comServicosEPecas() {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmDiagnostico();
        var prestacao = TestDataFactory.criarPrestacaoServicoValida();
        var alocacao = TestDataFactory.criarAlocacaoPecaValida();

        when(cadastrarOrdemServicoUseCase.executar(any(), any())).thenReturn(os);
        when(cadastrarPrestacaoServicoUseCase.executar(any(), any(), any())).thenReturn(prestacao);
        when(cadastrarAlocacaoPecaUseCase.executar(any(), any(), any())).thenReturn(alocacao);
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));

        List<ServicoAbrirCommand> servicos = List.of(
                new ServicoAbrirCommand(UUID.randomUUID(), new BigDecimal("150.00"),
                        List.of(new PecaAbrirCommand(UUID.randomUUID(), 2)))
        );

        OrdemServico resultado = useCase.executar("Observação", UUID.randomUUID(), servicos);

        assertThat(resultado).isNotNull();
        verify(cadastrarPrestacaoServicoUseCase, times(1)).executar(any(), any(), any());
        verify(cadastrarAlocacaoPecaUseCase, times(1)).executar(any(), any(), any());
    }

    @Test
    @DisplayName("Deve abrir OS com serviços sem peças")
    void deveAbrir_comServicosSemPecas() {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmDiagnostico();
        var prestacao = TestDataFactory.criarPrestacaoServicoValida();

        when(cadastrarOrdemServicoUseCase.executar(any(), any())).thenReturn(os);
        when(cadastrarPrestacaoServicoUseCase.executar(any(), any(), any())).thenReturn(prestacao);
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));

        List<ServicoAbrirCommand> servicos = List.of(
                new ServicoAbrirCommand(UUID.randomUUID(), new BigDecimal("150.00"), null)
        );

        OrdemServico resultado = useCase.executar("Observação", UUID.randomUUID(), servicos);

        assertThat(resultado).isNotNull();
        verify(cadastrarAlocacaoPecaUseCase, never()).executar(any(), any(), any());
    }

    @Test
    @DisplayName("Deve abrir OS com múltiplos serviços e peças")
    void deveAbrir_comMultiplosServicosEPecas() {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmDiagnostico();
        var prestacao = TestDataFactory.criarPrestacaoServicoValida();
        var alocacao = TestDataFactory.criarAlocacaoPecaValida();

        when(cadastrarOrdemServicoUseCase.executar(any(), any())).thenReturn(os);
        when(cadastrarPrestacaoServicoUseCase.executar(any(), any(), any())).thenReturn(prestacao);
        when(cadastrarAlocacaoPecaUseCase.executar(any(), any(), any())).thenReturn(alocacao);
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));

        List<ServicoAbrirCommand> servicos = List.of(
                new ServicoAbrirCommand(UUID.randomUUID(), new BigDecimal("150.00"),
                        List.of(new PecaAbrirCommand(UUID.randomUUID(), 1),
                                new PecaAbrirCommand(UUID.randomUUID(), 3))),
                new ServicoAbrirCommand(UUID.randomUUID(), new BigDecimal("200.00"),
                        List.of(new PecaAbrirCommand(UUID.randomUUID(), 2)))
        );

        OrdemServico resultado = useCase.executar("Observação", UUID.randomUUID(), servicos);

        assertThat(resultado).isNotNull();
        verify(cadastrarPrestacaoServicoUseCase, times(2)).executar(any(), any(), any());
        verify(cadastrarAlocacaoPecaUseCase, times(3)).executar(any(), any(), any());
    }

    @Test
    @DisplayName("Deve lançar exceção quando OS não encontrada ao buscar resultado final")
    void deveLancarExcecao_quandoOsNaoEncontradaNoFinal() {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmDiagnostico();
        var prestacao = TestDataFactory.criarPrestacaoServicoValida();

        when(cadastrarOrdemServicoUseCase.executar(any(), any())).thenReturn(os);
        when(cadastrarPrestacaoServicoUseCase.executar(any(), any(), any())).thenReturn(prestacao);
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.empty());

        List<ServicoAbrirCommand> servicos = List.of(
                new ServicoAbrirCommand(UUID.randomUUID(), new BigDecimal("150.00"), null)
        );

        assertThatThrownBy(() -> useCase.executar("Observação", UUID.randomUUID(), servicos))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
