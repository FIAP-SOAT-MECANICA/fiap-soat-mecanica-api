package br.com.fiap.soat.mecanica.adapters.in.web.security;

import br.com.fiap.soat.mecanica.domain.cliente.Cliente;
import br.com.fiap.soat.mecanica.domain.cliente.ClienteRepository;
import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.util.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentClienteProviderTest {

    @Mock
    private ClienteRepository clienteRepository;
    @InjectMocks
    private CurrentClienteProvider provider;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Deve retornar cliente autenticado com sucesso")
    void deveRetornar_quandoAutenticado() {
        // Arrange
        UUID clienteId = UUID.randomUUID();
        ClientePrincipal principal = new ClientePrincipal(clienteId);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        Cliente cliente = TestDataFactory.criarClienteComCpf();
        when(clienteRepository.buscarPorId(clienteId)).thenReturn(Optional.of(cliente));

        // Act
        Cliente resultado = provider.get();

        // Assert
        assertThat(resultado).isEqualTo(cliente);
    }

    @Test
    @DisplayName("Deve lançar exceção quando não autenticado")
    void deveLancarExcecao_quandoNaoAutenticado() {
        assertThatThrownBy(() -> provider.get())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Cliente não autenticado");
    }

    @Test
    @DisplayName("Deve lançar exceção quando cliente do token não existe mais")
    void deveLancarExcecao_quandoClienteNaoEncontrado() {
        // Arrange
        UUID clienteId = UUID.randomUUID();
        ClientePrincipal principal = new ClientePrincipal(clienteId);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(clienteRepository.buscarPorId(any())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> provider.get())
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
