package br.com.fiap.soat.mecanica.application.ordemServico.usecase;

import br.com.fiap.soat.mecanica.application.ordemServico.ListarOrdensServicoAtivasPort;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.Pagina;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.Paginacao;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.util.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarOrdensServicoAtivasUseCaseTest {

    @Mock
    private ListarOrdensServicoAtivasPort listarOrdensServicoAtivasPort;

    @InjectMocks
    private ListarOrdensServicoAtivasUseCase useCase;

    @Test
    void deveDelegarListagemPaginadaParaPorta() {
        Paginacao paginacao = new Paginacao(1, 20);
        OrdemServico ordemServico = TestDataFactory.criarOrdemServicoEmExecucao();
        Pagina<OrdemServico> pagina = new Pagina<>(List.of(ordemServico), 1, 20, 21, 2, false, true);
        when(listarOrdensServicoAtivasPort.listarAtivas(paginacao)).thenReturn(pagina);

        Pagina<OrdemServico> resultado = useCase.executar(paginacao);

        assertThat(resultado).isSameAs(pagina);
        verify(listarOrdensServicoAtivasPort).listarAtivas(paginacao);
    }
}
