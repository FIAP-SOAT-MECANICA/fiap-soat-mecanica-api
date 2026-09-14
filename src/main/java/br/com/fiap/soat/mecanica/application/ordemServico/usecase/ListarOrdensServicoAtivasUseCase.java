package br.com.fiap.soat.mecanica.application.ordemServico.usecase;

import br.com.fiap.soat.mecanica.application.ordemServico.ListarOrdensServicoAtivasPort;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.Pagina;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.Paginacao;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListarOrdensServicoAtivasUseCase {

    private final ListarOrdensServicoAtivasPort listarOrdensServicoAtivasPort;

    public Pagina<OrdemServico> executar(Paginacao paginacao) {
        return listarOrdensServicoAtivasPort.listarAtivas(paginacao);
    }
}
