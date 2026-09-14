package br.com.fiap.soat.mecanica.application.prestacaoServico;

import br.com.fiap.soat.mecanica.application.ordemServico.usecase.NotificarAlteracaoSituacaoOrdemServicoUseCase;
import br.com.fiap.soat.mecanica.domain.enums.SituacaoOrdemServicoEnum;
import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.domain.prestacaoServico.PrestacaoServico;
import br.com.fiap.soat.mecanica.domain.prestacaoServico.PrestacaoServicoRepository;
import br.com.fiap.soat.mecanica.domain.servico.Servico;
import br.com.fiap.soat.mecanica.domain.servico.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CadastrarPrestacaoServicoUseCase {

    private final PrestacaoServicoRepository prestacaoServicoRepository;
    private final ServicoRepository servicoRepository;
    private final OrdemServicoRepository ordemServicoRepository;
    private final NotificarAlteracaoSituacaoOrdemServicoUseCase notificarAlteracaoSituacaoOrdemServicoUseCase;

    @Transactional
    public PrestacaoServico executar(BigDecimal precoMaoDeObra, UUID ordemServicoId, UUID servicoId) {

        Servico servico = servicoRepository.buscarPorId(servicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Serviço não encontrado"));

        OrdemServico ordemServico = ordemServicoRepository.buscarPorId(ordemServicoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem Serviço não encontrado"));

        if (servico.isInativo() || ordemServico.isInativo())
            throw new RegraNegocioException("Serviço ou Ordem de Serviço está inativo");

        if (prestacaoServicoRepository.existsByOrdemServicoIdAndServicoId(ordemServicoId, servicoId))
            throw new RegraNegocioException("Serviço já adicionado na OS");

        ordemServico.validarPermiteCadastrarPrestacaoServico();

        PrestacaoServico prestacaoServico = new PrestacaoServico(precoMaoDeObra, ordemServicoId, servicoId);
        PrestacaoServico prestacaoSalva = prestacaoServicoRepository.salvar(prestacaoServico);

        ordemServico.adicionarValor(prestacaoSalva.getSubtotal());

        SituacaoOrdemServicoEnum situacaoAnterior = ordemServico.getSituacao();
        if (ordemServico.isRecebida()) {
            ordemServico.iniciarDiagnostico();
        }

        OrdemServico ordemServicoSalva = ordemServicoRepository.salvar(ordemServico);
        notificarAlteracaoSituacaoOrdemServicoUseCase.executar(ordemServicoSalva, situacaoAnterior);

        return prestacaoSalva;
    }
}
