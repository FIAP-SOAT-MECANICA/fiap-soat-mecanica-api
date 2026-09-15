package br.com.fiap.soat.mecanica.config.exception;

import br.com.fiap.soat.mecanica.adapters.in.web.exception.SenhaInvalidaException;
import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Deve incluir o identificador da requisição na resposta de erro")
    void deveIncluirErrorId() {
        MDC.put("requestId", "request-123");
        try {
            ResponseEntity<ErrorResponse> response = handler.handleGeneric(new Exception("Erro inesperado"));

            assertThat(response.getBody().errorId()).isEqualTo("request-123");
        } finally {
            MDC.remove("requestId");
        }
    }

    @Test
    @DisplayName("Deve retornar 422 para RegraNegocioException")
    void deveRetornar422_quandoRegraNegocio() {
        // Act
        ResponseEntity<ErrorResponse> response = handler.handleRegraNegocio(
                new RegraNegocioException("Erro de regra"));

        // Assert
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().mensagem()).isEqualTo("Erro de regra");
    }

    @Test
    @DisplayName("Deve retornar 404 para RecursoNaoEncontradoException")
    void deveRetornar404_quandoRecursoNaoEncontrado() {
        // Act
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new RecursoNaoEncontradoException("Não encontrado"));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Deve retornar 401 para SenhaInvalidaException")
    void deveRetornar401_quandoSenhaInvalida() {
        // Act
        ResponseEntity<ErrorResponse> response = handler.handleSenhaInvalida(
                new SenhaInvalidaException("Senha inválida"));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Deve retornar 400 para MethodArgumentNotValidException")
    void deveRetornar400_quandoValidacaoFalha() {
        // Arrange
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("obj", "campo", "é obrigatório");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        // Act
        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Deve retornar 400 para DataIntegrityViolationException")
    void deveRetornar400_quandoIntegridadeDados() {
        // Act
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(
                new DataIntegrityViolationException("Violation"));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Deve retornar 400 para IllegalArgumentException")
    void deveRetornar400_quandoArgumentoIlegal() {
        // Act
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(
                new IllegalArgumentException("Argumento inválido"));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Deve retornar 403 para AuthorizationDeniedException")
    void deveRetornar403_quandoAcessoNegado() {
        // Act
        ResponseEntity<ErrorResponse> response = handler.handleAuthorizationDenied(
                new AuthorizationDeniedException("Denied"));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Deve retornar 500 para exceção genérica")
    void deveRetornar500_quandoExcecaoGenerica() {
        // Act
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new Exception("Erro inesperado"));

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
