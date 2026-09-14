package br.com.fiap.soat.mecanica.config.exception;

import br.com.fiap.soat.mecanica.adapters.in.web.exception.SenhaInvalidaException;
import br.com.fiap.soat.mecanica.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.domain.exception.RegraNegocioException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.error("Erro: ", ex);
        List<String> detalhes = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return ResponseEntity.badRequest().body(
                new ErrorResponse(400, "Bad Request", "Parâmetro inválido", null, null, detalhes)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.error("Erro: ", ex);
        List<String> detalhes = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(
                new ErrorResponse(
                        400,
                        "Bad Request",
                        "Erro de validação",
                        null,
                        null,
                        detalhes
                )
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonError(HttpMessageNotReadableException ex) {
        log.error("Erro: ", ex);
        Throwable cause = ex.getCause();

        if (cause instanceof InvalidFormatException ife) {

            String fieldName = ife.getPath().stream()
                    .findFirst()
                    .map(JsonMappingException.Reference::getFieldName)
                    .orElse("campo desconhecido");

            Class<?> targetType = ife.getTargetType();

            if (targetType.isEnum()) {

                List<String> valoresPermitidos = Arrays.stream(targetType.getEnumConstants())
                        .map(Object::toString)
                        .toList();

                return ResponseEntity.badRequest().body(
                        new ErrorResponse(
                                400,
                                "Bad Request",
                                "Valor inválido para enum",
                                fieldName,
                                ife.getValue(),
                                valoresPermitidos
                        )
                );
            }
        }

        return ResponseEntity.badRequest().body(
                new ErrorResponse(
                        400,
                        "Bad Request",
                        "JSON inválido",
                        null,
                        null,
                        null
                )
        );
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(RecursoNaoEncontradoException ex) {
        log.error("Erro: ", ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse(
                        404,
                        "Not Found",
                        ex.getMessage(),
                        null,
                        null,
                        null
                )
        );
    }

    @ExceptionHandler(SenhaInvalidaException.class)
    public ResponseEntity<ErrorResponse> handleSenhaInvalida(SenhaInvalidaException ex) {
        log.error("Erro: ", ex);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ErrorResponse(
                        401,
                        "Unauthorized",
                        ex.getMessage(),
                        null,
                        null,
                        null
                )
        );
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErrorResponse> handleRegraNegocio(RegraNegocioException ex) {
        log.error("Erro: ", ex);
        return ResponseEntity.status(422).body(
                new ErrorResponse(
                        422,
                        "Unprocessable Entity",
                        ex.getMessage(),
                        null,
                        null,
                        null
                )
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Erro: ", ex);
        return ResponseEntity.badRequest().body(
                new ErrorResponse(
                        400,
                        "Bad Request",
                        "Violação de integridade dos dados.",
                        null,
                        null,
                        null
                )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.error("Erro: ", ex);
        return ResponseEntity.badRequest().body(
                new ErrorResponse(
                        400,
                        "Bad Request",
                        "Valor enviado incorretamente",
                        null,
                        null,
                        null
                )
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parametro invalido: {}={}", ex.getName(), ex.getValue());
        return ResponseEntity.badRequest().body(
                new ErrorResponse(
                        400,
                        "Bad Request",
                        "Parametro enviado em formato invalido",
                        ex.getName(),
                        ex.getValue(),
                        null
                )
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Metodo HTTP nao suportado: {}", ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
                new ErrorResponse(
                        405,
                        "Method Not Allowed",
                        "Metodo HTTP nao suportado para este recurso",
                        null,
                        ex.getMethod(),
                        null
                )
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Recurso inexistente: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse(
                        404,
                        "Not Found",
                        "Recurso nao encontrado",
                        null,
                        ex.getResourcePath(),
                        null
                )
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Erro: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                new ErrorResponse(
                        500,
                        "Internal Server Error",
                        "Erro interno inesperado",
                        null,
                        null,
                        null
                )
        );
    }

    @ExceptionHandler({
            AccessDeniedException.class,
            AuthorizationDeniedException.class
    })
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        log.error("Erro: ", ex);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                new ErrorResponse(
                        403,
                        "Forbidden",
                        "Você não tem permissão para acessar este recurso",
                        null,
                        null,
                        null
                )
        );
    }
}
