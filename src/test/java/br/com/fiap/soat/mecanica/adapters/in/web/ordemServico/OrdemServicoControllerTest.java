package br.com.fiap.soat.mecanica.adapters.in.web.ordemServico;

import br.com.fiap.soat.mecanica.adapters.in.web.security.CustomUserDetailsService;
import br.com.fiap.soat.mecanica.adapters.out.security.JwtService;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.TempoMedioOSResult;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.TempoMedioServicoResult;
import br.com.fiap.soat.mecanica.application.ordemServico.dto.Pagina;
import br.com.fiap.soat.mecanica.application.ordemServico.usecase.*;
import br.com.fiap.soat.mecanica.config.SecurityConfig;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = OrdemServicoController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class OrdemServicoControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private CadastrarOrdemServicoUseCase cadastrarUseCase;
    @MockitoBean
    private BuscarOrdemServicoPorIdUseCase buscarPorIdUseCase;
    @MockitoBean
    private EnviarOrdemServicoParaAprovacaoUseCase enviarAprovacaoUseCase;
    @MockitoBean
    private IniciarExecucaoOrdemServicoUseCase iniciarExecucaoUseCase;
    @MockitoBean
    private VoltarOrdemServicoParaDiagnosticoUseCase voltarDiagnosticoUseCase;
    @MockitoBean
    private CancelarOrdemServicoPorDesistenciaUseCase cancelarUseCase;
    @MockitoBean
    private PagarEEntregarOrdemServicoUseCase pagarEntregarUseCase;
    @MockitoBean
    private ConsultarTempoMedioOSUseCase consultarTempoMedioUseCase;
    @MockitoBean
    private BuscarTodosOrdemServicoPorVeiculoPlacaUseCase buscarTodosPorPlacaUseCase;
    @MockitoBean
    private ListarOrdensServicoAtivasUseCase listarOrdensServicoAtivasUseCase;
    @MockitoBean
    private AbrirOrdemServicoUseCase abrirOrdemServicoUseCase;
    @MockitoBean
    private AprovarOrcamentoUseCase aprovarOrcamentoUseCase;
    @MockitoBean
    private RecusarOrcamentoUseCase recusarOrcamentoUseCase;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve cadastrar OS com role MECANICO")
    void deveCadastrar_quandoMecanico() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoRecebida();
        when(cadastrarUseCase.executar(anyString(), any())).thenReturn(os);

        String json = """
                {"observacao":"Teste","veiculoId":"%s"}
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/ordem-servicos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve buscar OS por ID")
    void deveBuscarPorId() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoRecebida();
        when(buscarPorIdUseCase.executar(any())).thenReturn(os);

        mockMvc.perform(get("/ordem-servicos/{id}", os.getId()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve buscar todas OS por placa do veículo")
    void deveBuscarTodosPorVeiculoPlaca() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoRecebida();
        when(buscarTodosPorPlacaUseCase.executar(anyString())).thenReturn(List.of(os));

        mockMvc.perform(get("/ordem-servicos/veiculo/placa/{placa}", "ABC1234"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve listar OS ativas paginadas com role MECANICO")
    void deveListarAtivasPaginadas_quandoMecanico() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();
        when(listarOrdensServicoAtivasUseCase.executar(any()))
                .thenReturn(new Pagina<>(List.of(os), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/ordem-servicos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(os.getId().toString()))
                .andExpect(jsonPath("$.content[0].situacao").value("EM_EXECUCAO"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve retornar pagina vazia")
    void deveRetornarPaginaVazia() throws Exception {
        when(listarOrdensServicoAtivasUseCase.executar(any()))
                .thenReturn(new Pagina<>(List.of(), 3, 20, 0, 0, false, true));

        mockMvc.perform(get("/ordem-servicos").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve rejeitar parametros de paginacao invalidos")
    void deveRejeitarPaginacaoInvalida() throws Exception {
        mockMvc.perform(get("/ordem-servicos").param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/ordem-servicos").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ATENDENTE")
    @DisplayName("Deve negar listagem para usuario que nao e MECANICO")
    void deveNegarListagem_quandoNaoMecanico() throws Exception {
        mockMvc.perform(get("/ordem-servicos"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve pagar e entregar OS")
    void devePagarEEntregar() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoEntregue();
        when(pagarEntregarUseCase.executar(any())).thenReturn(os);

        mockMvc.perform(patch("/ordem-servicos/{id}/pagar", os.getId()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve iniciar execucao da OS")
    void deveIniciarExecucao() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();
        when(iniciarExecucaoUseCase.executar(any())).thenReturn(os);

        mockMvc.perform(patch("/ordem-servicos/{id}/iniciar-execucao", os.getId()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve enviar OS para aguardar aprovacao")
    void deveEnviarParaAguardarAprovacao() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoAguardandoAprovacao();
        when(enviarAprovacaoUseCase.executar(any())).thenReturn(os);

        mockMvc.perform(patch("/ordem-servicos/{id}/enviar-para-aguardar-aprovacao", os.getId()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve voltar OS para diagnostico")
    void deveVoltarDiagnostico() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmDiagnostico();
        when(voltarDiagnosticoUseCase.executar(any())).thenReturn(os);

        mockMvc.perform(patch("/ordem-servicos/{id}/voltar-diagnostico", os.getId()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve cancelar OS")
    void deveCancelar() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoInativa();
        when(cancelarUseCase.executar(any())).thenReturn(os);

        mockMvc.perform(patch("/ordem-servicos/{id}/cancelar", os.getId()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve buscar tempo medio dos servicos")
    void deveBuscarTempoMedioDosServicos() throws Exception {
        TempoMedioOSResult result = new TempoMedioOSResult(
                List.of(new TempoMedioServicoResult("Troca de oleo", 3600.0)),
                3600.0
        );
        when(consultarTempoMedioUseCase.executar(any())).thenReturn(result);

        mockMvc.perform(get("/ordem-servicos/{id}/tempo-medio", UUID.randomUUID()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve abrir OS com serviços e peças")
    void deveAbrirOS_comServicosEPecas() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmDiagnostico();
        when(abrirOrdemServicoUseCase.executar(any(), any(), any())).thenReturn(os);

        String json = """
                {
                  "veiculoId": "%s",
                  "observacao": "Revisão completa",
                  "servicos": [
                    {
                      "servicoId": "%s",
                      "precoMaoDeObra": 150.00,
                      "pecas": [
                        { "pecaId": "%s", "quantidade": 2 }
                      ]
                    }
                  ]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/ordem-servicos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MECANICO")
    @DisplayName("Deve retornar 400 ao abrir OS sem serviços")
    void deveRetornar400_quandoAbrirOSSemServicos() throws Exception {
        String json = """
                {
                  "veiculoId": "%s",
                  "servicos": []
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(post("/ordem-servicos/abrir")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("Deve aprovar orçamento — endpoint público")
    void deveAprovarOrcamento() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmExecucao();
        when(aprovarOrcamentoUseCase.executar(any())).thenReturn(os);

        mockMvc.perform(patch("/ordem-servicos/{id}/aprovar-orcamento", os.getId()).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("Deve recusar orçamento — endpoint público")
    void deveRecusarOrcamento() throws Exception {
        OrdemServico os = TestDataFactory.criarOrdemServicoEmDiagnostico();
        when(recusarOrcamentoUseCase.executar(any())).thenReturn(os);

        mockMvc.perform(patch("/ordem-servicos/{id}/recusar-orcamento", os.getId()).with(csrf()))
                .andExpect(status().isOk());
    }
}
