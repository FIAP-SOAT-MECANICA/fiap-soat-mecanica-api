package br.com.fiap.soat.mecanica.application.ordemServico;

import br.com.fiap.soat.mecanica.application.ordemServico.dto.Pagina;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.Paginacao;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;

public interface ListarOrdensServicoAtivasPort {

    Pagina<OrdemServico> listarAtivas(Paginacao paginacao);
}
