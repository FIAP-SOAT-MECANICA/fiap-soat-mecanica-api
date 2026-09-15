package br.com.fiap.soat.mecanica.config.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestLoggingFilterTest {

    private final HttpRequestLoggingFilter filter = new HttpRequestLoggingFilter();

    @Test
    void devePropagarRequestIdValido() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ordens-servico/1");
        request.addHeader(HttpRequestLoggingFilter.REQUEST_ID_HEADER, "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdNoFluxo = new AtomicReference<>();

        filter.doFilterInternal(request, response, (req, res) -> requestIdNoFluxo.set(MDC.get("requestId")));

        assertThat(requestIdNoFluxo).hasValue("request-123");
        assertThat(response.getHeader(HttpRequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("request-123");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void deveSubstituirRequestIdInvalido() throws IOException, jakarta.servlet.ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ordens-servico");
        request.addHeader(HttpRequestLoggingFilter.REQUEST_ID_HEADER, "valor com espacos\nindevido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, (req, res) -> { });

        assertThat(response.getHeader(HttpRequestLoggingFilter.REQUEST_ID_HEADER))
                .isNotBlank()
                .isNotEqualTo("valor com espacos\nindevido");
    }
}
