package br.com.fiap.soat.mecanica.application.ordemServico.usecase;

import br.com.fiap.soat.mecanica.adapters.in.web.security.CurrentUserProvider;
import br.com.fiap.soat.mecanica.domain.enums.CargoEnum;
import br.com.fiap.soat.mecanica.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServico;
import br.com.fiap.soat.mecanica.domain.ordemServico.OrdemServicoRepository;
import br.com.fiap.soat.mecanica.domain.usuario.Usuario;
import br.com.fiap.soat.mecanica.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CadastrarOrdemServicoUseCaseTest {

    @Mock
    private OrdemServicoRepository repository;
    @Mock
    private CurrentUserProvider currentUser;
    @Mock
    private NotificarAlteracaoSituacaoOrdemServicoUseCase notificacaoUseCase;
    @InjectMocks
    private CadastrarOrdemServicoUseCase useCase;

    @Test
    @DisplayName("Deve cadastrar OS quando usuário é mecânico")
    void deveCadastrar_quandoUsuarioMecanico() {
        // Arrange
        Usuario mecanico = TestDataFactory.criarUsuarioMecanico();
        when(currentUser.get()).thenReturn(mecanico);
        OrdemServico os = TestDataFactory.criarOrdemServicoRecebida();
        when(repository.salvar(any())).thenReturn(os);

        // Act
        OrdemServico resultado = useCase.executar("Obs", UUID.randomUUID());

        // Assert
        assertThat(resultado).isNotNull();
        verifyNoInteractions(notificacaoUseCase);
    }

    @Test
    @DisplayName("Deve lançar exceção quando usuário não é mecânico")
    void deveLancarExcecao_quandoUsuarioNaoMecanico() {
        // Arrange
        Usuario atendente = TestDataFactory.criarUsuarioAtendente();
        when(currentUser.get()).thenReturn(atendente);

        // Act & Assert
        assertThatThrownBy(() -> useCase.executar("Obs", UUID.randomUUID()))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessage("Somente mecânicos podem criar OS");
    }
}
