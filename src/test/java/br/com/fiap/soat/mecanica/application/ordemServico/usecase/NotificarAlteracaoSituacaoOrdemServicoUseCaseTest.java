package br.com.fiap.soat.mecanica.application.ordemServico.usecase;

import br.com.fiap.soat.mecanica.application.ordemServico.NotificacaoOrdemServicoPort;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.NotificacaoOrdemServico;
import br.com.fiap.soat.mecanica.domain.cliente.Cliente;
import br.com.fiap.soat.mecanica.domain.cliente.ClienteRepository;
import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.veiculo.Veiculo;
import br.com.fiap.soat.mecanica.domain.veiculo.VeiculoRepository;
import br.com.fiap.soat.mecanica.util.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificarAlteracaoSituacaoOrdemServicoUseCaseTest {

    @Mock
    private VeiculoRepository veiculoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private NotificacaoOrdemServicoPort notificacaoOrdemServicoPort;

    @AfterEach
    void limparSincronizacaoTransacional() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void deveResolverClienteEEnviarDadosCorretos() {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();
        Veiculo veiculo = TestDataFactory.criarVeiculoValido();
        Cliente cliente = TestDataFactory.criarClienteComCpf();
        when(veiculoRepository.buscarPorId(os.getVeiculoId())).thenReturn(Optional.of(veiculo));
        when(clienteRepository.buscarPorId(veiculo.getClienteId())).thenReturn(Optional.of(cliente));
        NotificarAlteracaoSituacaoOrdemServicoUseCase useCase = criarUseCase(true);

        useCase.executar(os, SituacaoOrdemServicoEnum.AGUARDANDO_APROVACAO);

        ArgumentCaptor<NotificacaoOrdemServico> captor = ArgumentCaptor.forClass(NotificacaoOrdemServico.class);
        verify(notificacaoOrdemServicoPort).enviar(captor.capture());
        NotificacaoOrdemServico notificacao = captor.getValue();
        assertThat(notificacao.ordemServicoId()).isEqualTo(os.getId());
        assertThat(notificacao.nomeCliente()).isEqualTo(cliente.getNome());
        assertThat(notificacao.emailCliente()).isEqualTo(cliente.getEmail().getValue());
        assertThat(notificacao.situacaoAnterior()).isEqualTo(SituacaoOrdemServicoEnum.AGUARDANDO_APROVACAO);
        assertThat(notificacao.novaSituacao()).isEqualTo(SituacaoOrdemServicoEnum.EM_EXECUCAO);
    }

    @Test
    void deveEnviarSomenteDepoisDoCommit() {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();
        Veiculo veiculo = TestDataFactory.criarVeiculoValido();
        Cliente cliente = TestDataFactory.criarClienteComCpf();
        when(veiculoRepository.buscarPorId(os.getVeiculoId())).thenReturn(Optional.of(veiculo));
        when(clienteRepository.buscarPorId(veiculo.getClienteId())).thenReturn(Optional.of(cliente));
        NotificarAlteracaoSituacaoOrdemServicoUseCase useCase = criarUseCase(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        useCase.executar(os, SituacaoOrdemServicoEnum.AGUARDANDO_APROVACAO);

        verifyNoInteractions(notificacaoOrdemServicoPort);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(notificacaoOrdemServicoPort).enviar(any());
    }

    @Test
    void naoDeveEnviarQuandoDesabilitadaOuSemMudanca() {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();

        criarUseCase(false).executar(os, SituacaoOrdemServicoEnum.AGUARDANDO_APROVACAO);
        criarUseCase(true).executar(os, SituacaoOrdemServicoEnum.EM_EXECUCAO);

        verifyNoInteractions(veiculoRepository, clienteRepository, notificacaoOrdemServicoPort);
    }

    @Test
    void falhaDeResolucaoOuEnvioNaoDeveEscapar() {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();
        when(veiculoRepository.buscarPorId(os.getVeiculoId())).thenReturn(Optional.empty());

        assertThatCode(() -> criarUseCase(true)
                .executar(os, SituacaoOrdemServicoEnum.AGUARDANDO_APROVACAO))
                .doesNotThrowAnyException();
        verifyNoInteractions(notificacaoOrdemServicoPort);

        reset(veiculoRepository, clienteRepository, notificacaoOrdemServicoPort);
        Veiculo veiculo = TestDataFactory.criarVeiculoValido();
        Cliente cliente = TestDataFactory.criarClienteComCpf();
        when(veiculoRepository.buscarPorId(os.getVeiculoId())).thenReturn(Optional.of(veiculo));
        when(clienteRepository.buscarPorId(veiculo.getClienteId())).thenReturn(Optional.of(cliente));
        doThrow(new IllegalStateException("SMTP indisponivel"))
                .when(notificacaoOrdemServicoPort).enviar(any());

        assertThatCode(() -> criarUseCase(true)
                .executar(os, SituacaoOrdemServicoEnum.AGUARDANDO_APROVACAO))
                .doesNotThrowAnyException();
        verify(notificacaoOrdemServicoPort).enviar(any());
    }

    private NotificarAlteracaoSituacaoOrdemServicoUseCase criarUseCase(boolean habilitada) {
        return new NotificarAlteracaoSituacaoOrdemServicoUseCase(
                veiculoRepository,
                clienteRepository,
                notificacaoOrdemServicoPort,
                habilitada
        );
    }
}
