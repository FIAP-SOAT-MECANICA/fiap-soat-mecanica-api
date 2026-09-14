package br.com.fiap.soat.mecanica.application.prestacaoServico;

import br.com.fiap.soat.mecanica.application.ordemServico.usecase.NotificarAlteracaoSituacaoOrdemServicoUseCase;
import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.domain.prestacaoServico.PrestacaoServico;
import br.com.fiap.soat.mecanica.domain.prestacaoServico.PrestacaoServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinalizarPrestacaoServicoUseCase {

    private final PrestacaoServicoRepository prestacaoServicoRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final NotificarAlteracaoSituacaoOrdemServicoUseCase notificarAlteracaoSituacaoOrdemServicoUseCase;

    @Transactional
    public PrestacaoServico executar(UUID id) {

        PrestacaoServico ps = prestacaoServicoRepository.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Prestação de serviço não encontrada"));

        OrdemServico os = ordemServicoRepository.buscarPorId(ps.getOrdemServicoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de serviço não encontrada"));

        if (!os.isEmExecucao()) {
            throw new RegraNegocioException("Ordem de serviço deve estar em execução para finalizar prestação");
        }

        ps.finalizarServico();

        PrestacaoServico psSalva = prestacaoServicoRepository.salvar(ps);

        List<PrestacaoServico> prestacoesAtivas = prestacaoServicoRepository
                .buscarTodosPorOrdemServicoId(psSalva.getOrdemServicoId())
                .stream()
                .filter(PrestacaoServico::isAtivo)
                .toList();

        boolean todasFinalizadas = !prestacoesAtivas.isEmpty()
                && prestacoesAtivas.stream().allMatch(PrestacaoServico::isFinalizada);

        if (todasFinalizadas) {
            SituacaoOrdemServicoEnum situacaoAnterior = os.getSituacao();
            os.finalizar();
            OrdemServico ordemServicoSalva = ordemServicoRepository.salvar(os);
            notificarAlteracaoSituacaoOrdemServicoUseCase.executar(ordemServicoSalva, situacaoAnterior);
        }

        return psSalva;
    }
}
