package br.com.fiap.soat.mecanica.adapters.in.web.security;

import br.com.fiap.soat.mecanica.adapters.out.security.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Deve autenticar quando token válido")
    void deveAutenticar_quandoTokenValido() throws Exception {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractUsername("valid-token")).thenReturn("teste@email.com");
        UserDetails userDetails = new User("teste@email.com", "pass", List.of());
        when(userDetailsService.loadUserByUsername("teste@email.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid-token", userDetails)).thenReturn(true);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Deve seguir sem autenticação quando sem header")
    void deveSeguir_quandoSemHeader() throws Exception {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn(null);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Deve seguir sem autenticação quando header inválido")
    void deveSeguir_quandoHeaderInvalido() throws Exception {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Basic invalid");

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Deve autenticar cliente quando token do Auth é válido")
    void deveAutenticarCliente_quandoTokenDeClienteValido() throws Exception {
        // Arrange
        UUID clienteId = UUID.randomUUID();
        when(request.getHeader("Authorization")).thenReturn("Bearer client-token");
        when(jwtService.extractClienteId("client-token")).thenReturn(Optional.of(clienteId));

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(new ClientePrincipal(clienteId));
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CLIENTE");
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Deve cair no fluxo interno quando token não é de cliente")
    void deveTentarFluxoInterno_quandoTokenNaoEhDeCliente() throws Exception {
        // Arrange
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        when(jwtService.extractClienteId("valid-token")).thenReturn(Optional.empty());
        when(jwtService.extractUsername("valid-token")).thenReturn("teste@email.com");
        UserDetails userDetails = new User("teste@email.com", "pass", List.of());
        when(userDetailsService.loadUserByUsername("teste@email.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("valid-token", userDetails)).thenReturn(true);

        // Act
        filter.doFilterInternal(request, response, filterChain);

        // Assert
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }
}
