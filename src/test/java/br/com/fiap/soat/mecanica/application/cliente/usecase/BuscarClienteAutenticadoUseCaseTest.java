package br.com.fiap.soat.mecanica.application.cliente.usecase;

import br.com.fiap.soat.mecanica.application.security.CurrentClientePort;
import br.com.fiap.soat.mecanica.domain.cliente.Cliente;
import br.com.fiap.soat.mecanica.util.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarClienteAutenticadoUseCaseTest {

    @Mock
    private CurrentClientePort currentClientePort;
    @InjectMocks
    private BuscarClienteAutenticadoUseCase useCase;

    @Test
    @DisplayName("Deve retornar o cliente autenticado atual")
    void deveRetornar_quandoAutenticado() {
        // Arrange
        Cliente cliente = TestDataFactory.criarClienteComCpf();
        when(currentClientePort.get()).thenReturn(cliente);

        // Act
        Cliente resultado = useCase.executar();

        // Assert
        assertThat(resultado).isEqualTo(cliente);
    }
}
