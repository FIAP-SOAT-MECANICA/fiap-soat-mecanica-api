package br.com.fiap.soat.mecanica.config.exception;

import org.slf4j.MDC;

import java.util.List;
import java.util.UUID;

public record ErrorResponse(
        int status,
        String erro,
        String mensagem,
        String campo,
        Object valorRecebido,
        List<String> detalhes,
        String errorId
) {
    public ErrorResponse {
        if (errorId == null) {
            String requestId = MDC.get("requestId");
            errorId = requestId == null ? UUID.randomUUID().toString() : requestId;
        }
    }

    public ErrorResponse(int status, String erro, String mensagem, String campo, Object valorRecebido,
                         List<String> detalhes) {
        this(status, erro, mensagem, campo, valorRecebido, detalhes, null);
    }
}
