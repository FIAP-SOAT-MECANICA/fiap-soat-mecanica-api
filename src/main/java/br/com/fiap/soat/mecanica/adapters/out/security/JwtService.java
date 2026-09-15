package br.com.fiap.soat.mecanica.adapters.out.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    private static final String CLAIM_PRINCIPAL_TYPE = "principal_type";
    private static final String PRINCIPAL_TYPE_CLIENTE = "CLIENTE";

    private final Key internalKey;
    private final Key clienteKey;
    private final String clienteIssuer;
    private final String clienteAudience;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${auth.jwt.secret:}") String clienteSecret,
            @Value("${auth.jwt.issuer:fiap-soat-mecanica-auth}") String clienteIssuer,
            @Value("${auth.jwt.audience:fiap-soat-mecanica-api}") String clienteAudience
    ) {
        this.internalKey = Keys.hmacShaKeyFor(
                io.jsonwebtoken.io.Decoders.BASE64.decode(secret)
        );
        this.clienteKey = (clienteSecret == null || clienteSecret.isBlank())
                ? null
                : Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(clienteSecret));
        this.clienteIssuer = clienteIssuer;
        this.clienteAudience = clienteAudience;
    }

    // ---- usuários internos (MECANICO/ATENDENTE/ALMOXARIFE) ----

    // gerar token
    public String gerarToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000)) //JWT DURA 1 HORA
                .signWith(internalKey)
                .compact();
    }

    // extrair username (email)
    public String extractUsername(String token) {
        return parseClaims(token, internalKey).getSubject();
    }

    // validar token
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isExpired(parseClaims(token, internalKey));
    }

    // ---- clientes (token emitido pela Auth serverless) ----

    /**
     * Valida o token contra a chave do Auth e confere issuer/audience/principal_type.
     * Retorna vazio (nunca lança) para qualquer token que não seja um JWT de cliente
     * válido, permitindo que o filtro tente o fluxo de usuário interno em seguida.
     */
    public Optional<UUID> extractClienteId(String token) {
        if (clienteKey == null) {
            return Optional.empty();
        }

        try {
            Claims claims = parseClaims(token, clienteKey);

            if (!PRINCIPAL_TYPE_CLIENTE.equals(claims.get(CLAIM_PRINCIPAL_TYPE, String.class))) {
                return Optional.empty();
            }
            if (!clienteIssuer.equals(claims.getIssuer())) {
                return Optional.empty();
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(clienteAudience)) {
                return Optional.empty();
            }
            if (isExpired(claims)) {
                return Optional.empty();
            }

            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // ---- compartilhado ----

    private boolean isExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration != null && expiration.before(new Date());
    }

    private Claims parseClaims(String token, Key key) {
        return Jwts.parser()
                .verifyWith((SecretKey) key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
