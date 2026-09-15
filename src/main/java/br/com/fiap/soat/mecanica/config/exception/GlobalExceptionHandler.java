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
        log.atDebug().addKeyValue("event", "request_constraint_violation")
                .addKeyValue("violations", ex.getConstraintViolations().size()).log("Request constraint violation");
        List<String> detalhes = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return ResponseEntity.badRequest().body(
                new ErrorResponse(400, "Bad Request", "Parâmetro inválido", null, null, detalhes)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        log.atDebug().addKeyValue("event", "request_validation_failed")
                .addKeyValue("fieldErrorCount", ex.getBindingResult().getFieldErrorCount()).log("Request validation failed");
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
        log.atDebug().addKeyValue("event", "request_body_not_readable")
                .addKeyValue("exceptionType", ex.getClass().getSimpleName()).log("Request body is not readable");
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
        log.atInfo().addKeyValue("event", "resource_not_found")
                .addKeyValue("reason", ex.getMessage()).log("Resource not found");
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
        log.atWarn().addKeyValue("event", "authentication_failed")
                .addKeyValue("reason", ex.getClass().getSimpleName()).log("Authentication failed");
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
        log.atInfo().addKeyValue("event", "business_rule_rejected")
                .addKeyValue("reason", ex.getMessage()).log("Business rule rejected request");
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
        log.atWarn().addKeyValue("event", "data_integrity_violation")
                .addKeyValue("exceptionType", ex.getClass().getSimpleName()).log("Data integrity violation");
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
        log.atDebug().addKeyValue("event", "illegal_argument")
                .addKeyValue("exceptionType", ex.getClass().getSimpleName()).log("Illegal argument");
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
        log.atDebug().addKeyValue("event", "request_parameter_type_mismatch")
                .addKeyValue("parameter", ex.getName()).log("Request parameter type mismatch");
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
        log.atInfo().addKeyValue("event", "http_method_not_supported")
                .addKeyValue("method", ex.getMethod()).log("HTTP method not supported");
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
        log.atDebug().addKeyValue("event", "http_resource_not_found")
                .addKeyValue("path", ex.getResourcePath()).log("HTTP resource not found");
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
        log.atError().setCause(ex).addKeyValue("event", "unexpected_error").log("Unexpected application error");
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
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(Exception ex) {
        log.atWarn().addKeyValue("event", "authorization_denied")
                .addKeyValue("exceptionType", ex.getClass().getSimpleName()).log("Authorization denied");
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
