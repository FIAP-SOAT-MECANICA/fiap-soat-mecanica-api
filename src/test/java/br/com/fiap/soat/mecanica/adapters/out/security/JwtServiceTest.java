package br.com.fiap.soat.mecanica.adapters.out.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String INTERNAL_SECRET = "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhzNTEyLWFsZ29yaXRobS10ZXN0aW5n";
    private static final String CLIENTE_SECRET = "b3V0cmEtY2hhdmUtZGUtdGVzdGUtdGFtYmVtLWxvbmdhLW8tc3VmaWNpZW50ZS1wYXJhLWhzMjU2";
    private static final String ISSUER = "fiap-soat-mecanica-auth";
    private static final String AUDIENCE = "fiap-soat-mecanica-api";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(INTERNAL_SECRET, CLIENTE_SECRET, ISSUER, AUDIENCE);
    }

    private String clienteToken(UUID subject, String issuer, String audience, String principalType, Date expiration) {
        Key key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(CLIENTE_SECRET));
        var builder = Jwts.builder()
                .subject(subject.toString())
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(key);
        if (issuer != null) builder.issuer(issuer);
        if (audience != null) builder.audience().add(audience).and();
        if (principalType != null) builder.claim("principal_type", principalType);
        return builder.compact();
    }

    @Test
    @DisplayName("Deve gerar token JWT válido")
    void deveGerarToken_quandoEmailValido() {
        String token = jwtService.gerarToken("teste@email.com");

        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Deve extrair username do token")
    void deveExtrairUsername_quandoTokenValido() {
        String token = jwtService.gerarToken("teste@email.com");

        String username = jwtService.extractUsername(token);

        assertThat(username).isEqualTo("teste@email.com");
    }

    @Test
    @DisplayName("Deve validar token como válido")
    void deveValidarToken_quandoTokenValido() {
        String token = jwtService.gerarToken("teste@email.com");
        UserDetails userDetails = new User("teste@email.com", "pass", List.of());

        boolean valid = jwtService.isTokenValid(token, userDetails);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("Deve invalidar token quando username diferente")
    void deveInvalidarToken_quandoUsernameDiferente() {
        String token = jwtService.gerarToken("teste@email.com");
        UserDetails userDetails = new User("outro@email.com", "pass", List.of());

        boolean valid = jwtService.isTokenValid(token, userDetails);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("Deve extrair id do cliente quando token do Auth é válido")
    void deveExtrairClienteId_quandoTokenValido() {
        UUID clienteId = UUID.randomUUID();
        String token = clienteToken(clienteId, ISSUER, AUDIENCE, "CLIENTE",
                new Date(System.currentTimeMillis() + 3600000));

        var result = jwtService.extractClienteId(token);

        assertThat(result).contains(clienteId);
    }

    @Test
    @DisplayName("Deve rejeitar token de cliente com issuer diferente")
    void deveRejeitarClienteToken_quandoIssuerDiferente() {
        String token = clienteToken(UUID.randomUUID(), "outro-issuer", AUDIENCE, "CLIENTE",
                new Date(System.currentTimeMillis() + 3600000));

        assertThat(jwtService.extractClienteId(token)).isEmpty();
    }

    @Test
    @DisplayName("Deve rejeitar token de cliente com audience diferente")
    void deveRejeitarClienteToken_quandoAudienceDiferente() {
        String token = clienteToken(UUID.randomUUID(), ISSUER, "outra-audience", "CLIENTE",
                new Date(System.currentTimeMillis() + 3600000));

        assertThat(jwtService.extractClienteId(token)).isEmpty();
    }

    @Test
    @DisplayName("Deve rejeitar token sem principal_type CLIENTE")
    void deveRejeitarClienteToken_quandoPrincipalTypeDiferente() {
        String token = clienteToken(UUID.randomUUID(), ISSUER, AUDIENCE, "FUNCIONARIO",
                new Date(System.currentTimeMillis() + 3600000));

        assertThat(jwtService.extractClienteId(token)).isEmpty();
    }

    @Test
    @DisplayName("Deve rejeitar token de cliente expirado")
    void deveRejeitarClienteToken_quandoExpirado() {
        String token = clienteToken(UUID.randomUUID(), ISSUER, AUDIENCE, "CLIENTE",
                new Date(System.currentTimeMillis() - 1000));

        assertThat(jwtService.extractClienteId(token)).isEmpty();
    }

    @Test
    @DisplayName("Deve rejeitar token assinado com outra chave")
    void deveRejeitarClienteToken_quandoAssinaturaInvalida() {
        String tokenInterno = jwtService.gerarToken("teste@email.com");

        assertThat(jwtService.extractClienteId(tokenInterno)).isEmpty();
    }

    @Test
    @DisplayName("Deve ignorar token de cliente quando chave do Auth não está configurada")
    void deveIgnorarClienteToken_quandoChaveNaoConfigurada() {
        JwtService semChaveCliente = new JwtService(INTERNAL_SECRET, "", ISSUER, AUDIENCE);
        String token = clienteToken(UUID.randomUUID(), ISSUER, AUDIENCE, "CLIENTE",
                new Date(System.currentTimeMillis() + 3600000));

        assertThat(semChaveCliente.extractClienteId(token)).isEmpty();
    }
}
