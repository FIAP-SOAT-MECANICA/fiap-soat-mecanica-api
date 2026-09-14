package br.com.fiap.soat.mecanica.application.ordemServico.usecase;

import br.com.fiap.soat.mecanica.application.alocacaoPeca.CadastrarAlocacaoPecaUseCase;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.ServicoAbrirCommand;
import br.com.fiap.soat.mecanica.application.prestacaoServico.CadastrarPrestacaoServicoUseCase;
import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.domain.prestacaoServico.PrestacaoServico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AbrirOrdemServicoUseCase {

    private final CadastrarOrdemServicoUseCase cadastrarOrdemServicoUseCase;
    private final CadastrarPrestacaoServicoUseCase cadastrarPrestacaoServicoUseCase;
    private final CadastrarAlocacaoPecaUseCase cadastrarAlocacaoPecaUseCase;
    private final OrdemServicoRepository ordemServicoRepository;

    @Transactional
    public OrdemServico executar(String observacao, UUID veiculoId, List<ServicoAbrirCommand> servicos) {

        OrdemServico os = cadastrarOrdemServicoUseCase.executar(observacao, veiculoId);

        for (ServicoAbrirCommand servico : servicos) {
            PrestacaoServico prestacao = cadastrarPrestacaoServicoUseCase.executar(
                    servico.precoMaoDeObra(), os.getId(), servico.servicoId());

            if (servico.pecas() != null) {
                servico.pecas().forEach(peca ->
                        cadastrarAlocacaoPecaUseCase.executar(
                                peca.quantidade(), prestacao.getId(), peca.pecaId()));
            }
        }

        return ordemServicoRepository.buscarPorId(os.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ordem de serviço não encontrada"));
    }
}
