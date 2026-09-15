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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.config.observability.BusinessEventLogger.ordemServicoCancelada;

@Service
@RequiredArgsConstructor
public class CancelarOrdemServicoPorDesistenciaUseCase {

    private final OrdemServicoRepository ordemServicoRepository;
    private final PrestacaoServicoRepository prestacaoServicoRepository;
    private final AlocacaoPecaRepository alocacaoPecaRepository;
    private final PecaRepository pecaRepository;

    @Transactional
    public OrdemServico executar(UUID osId) {

        OrdemServico os = ordemServicoRepository.buscarPorId(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de serviço não encontrada"));

        os.cancelarPorDesistencia();

        List<PrestacaoServico> prestacoes =
                prestacaoServicoRepository.buscarTodosPorOrdemServicoId(osId);

        for (PrestacaoServico prestacao : prestacoes) {
            List<AlocacaoPeca> alocacoes =
                    alocacaoPecaRepository.buscarTodosPorPrestacaoServicoId(prestacao.getId());

            for (AlocacaoPeca alocacao : alocacoes) {
                if (alocacao.isAtivo()) {
                    Peca peca = pecaRepository.buscarPorId(alocacao.getPecaId())
                            .orElseThrow(() -> new RecursoNaoEncontradoException("Peça não encontrada"));

                    peca.reporEstoque(alocacao.getQuantidadeNecessaria());
                    pecaRepository.salvar(peca);

                    alocacao.inativar();
                    alocacaoPecaRepository.salvar(alocacao);
                }
            }

            if (prestacao.isAtivo()) {
                prestacao.inativar();
                prestacaoServicoRepository.salvar(prestacao);
            }
        }

        OrdemServico ordemServicoSalva = ordemServicoRepository.salvar(os);
        ordemServicoCancelada(ordemServicoSalva.getId());
        return ordemServicoSalva;
    }
}
