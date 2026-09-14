package br.com.fiap.soat.mecanica.application.prestacaoServico;

import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import br.com.fiap.soat.mecanica.application.ordemServico.usecase.NotificarAlteracaoSituacaoOrdemServicoUseCase;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.domain.prestacaoServico.PrestacaoServico;
import br.com.fiap.soat.mecanica.domain.prestacaoServico.PrestacaoServicoRepository;
import br.com.fiap.soat.mecanica.domain.servico.Servico;
import br.com.fiap.soat.mecanica.domain.servico.ServicoRepository;
import br.com.fiap.soat.mecanica.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CadastrarPrestacaoServicoUseCaseTest {

    @Mock
    private PrestacaoServicoRepository prestacaoServicoRepository;
    @Mock
    private ServicoRepository servicoRepository;
    @Mock
    private OrdemServicoRepository ordemServicoRepository;
    @Mock
    private NotificarAlteracaoSituacaoOrdemServicoUseCase notificarAlteracaoSituacaoOrdemServicoUseCase;
    @InjectMocks
    private CadastrarPrestacaoServicoUseCase useCase;

    @Test
    @DisplayName("Deve cadastrar prestação quando OS recebida (muda para diagnóstico)")
    void deveCadastrar_quandoOsRecebida() {
        // Arrange
        Servico servico = TestDataFactory.criarServicoValido();
        OrdemServico os = TestDataFactory.criarOrdemServicoRecebida();
        PrestacaoServico ps = TestDataFactory.criarPrestacaoServicoValida();

        when(servicoRepository.buscarPorId(any())).thenReturn(Optional.of(servico));
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(prestacaoServicoRepository.existsByOrdemServicoIdAndServicoId(any(), any())).thenReturn(false);
        when(prestacaoServicoRepository.salvar(any())).thenReturn(ps);
        when(ordemServicoRepository.salvar(any())).thenReturn(os);

        // Act
        PrestacaoServico resultado = useCase.executar(new BigDecimal("200"), os.getId(), servico.getId());

        // Assert
        assertThat(resultado).isNotNull();
        verify(notificarAlteracaoSituacaoOrdemServicoUseCase)
                .executar(os, SituacaoOrdemServicoEnum.RECEBIDA);
    }

    @Test
    @DisplayName("Deve cadastrar prestação quando OS em diagnóstico")
    void deveCadastrar_quandoOsEmDiagnostico() {
        // Arrange
        Servico servico = TestDataFactory.criarServicoValido();
        OrdemServico os = TestDataFactory.criarOrdemServicoEmDiagnostico();
        PrestacaoServico ps = TestDataFactory.criarPrestacaoServicoValida();

        when(servicoRepository.buscarPorId(any())).thenReturn(Optional.of(servico));
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(prestacaoServicoRepository.existsByOrdemServicoIdAndServicoId(any(), any())).thenReturn(false);
        when(prestacaoServicoRepository.salvar(any())).thenReturn(ps);
        when(ordemServicoRepository.salvar(any())).thenReturn(os);

        // Act
        PrestacaoServico resultado = useCase.executar(new BigDecimal("200"), os.getId(), servico.getId());

        // Assert
        assertThat(resultado).isNotNull();
        verify(notificarAlteracaoSituacaoOrdemServicoUseCase)
                .executar(os, SituacaoOrdemServicoEnum.EM_DIAGNOSTICO);
    }

    @Test
    @DisplayName("Deve lançar exceção quando serviço não encontrado")
    void deveLancarExcecao_quandoServicoNaoEncontrado() {
        when(servicoRepository.buscarPorId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.executar(new BigDecimal("200"), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando OS não encontrada")
    void deveLancarExcecao_quandoOsNaoEncontrada() {
        Servico servico = TestDataFactory.criarServicoValido();
        when(servicoRepository.buscarPorId(any())).thenReturn(Optional.of(servico));
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.executar(new BigDecimal("200"), UUID.randomUUID(), servico.getId()))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando serviço inativo")
    void deveLancarExcecao_quandoServicoInativo() {
        Servico servico = TestDataFactory.criarServicoValido();
        servico.inativar();
        OrdemServico os = TestDataFactory.criarOrdemServicoRecebida();
        when(servicoRepository.buscarPorId(any())).thenReturn(Optional.of(servico));
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        assertThatThrownBy(() -> useCase.executar(new BigDecimal("200"), os.getId(), servico.getId()))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando serviço já adicionado na OS")
    void deveLancarExcecao_quandoServicoJaAdicionado() {
        Servico servico = TestDataFactory.criarServicoValido();
        OrdemServico os = TestDataFactory.criarOrdemServicoRecebida();
        when(servicoRepository.buscarPorId(any())).thenReturn(Optional.of(servico));
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(prestacaoServicoRepository.existsByOrdemServicoIdAndServicoId(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> useCase.executar(new BigDecimal("200"), os.getId(), servico.getId()))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando OS não permite cadastro de prestação")
    void deveLancarExcecao_quandoOsNaoPermiteCadastro() {
        Servico servico = TestDataFactory.criarServicoValido();
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();
        when(servicoRepository.buscarPorId(any())).thenReturn(Optional.of(servico));
        when(ordemServicoRepository.buscarPorId(any())).thenReturn(Optional.of(os));
        when(prestacaoServicoRepository.existsByOrdemServicoIdAndServicoId(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> useCase.executar(new BigDecimal("200"), os.getId(), servico.getId()))
                .isInstanceOf(RegraNegocioException.class);
    }
}
