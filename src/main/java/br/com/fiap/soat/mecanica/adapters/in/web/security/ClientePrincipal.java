package br.com.fiap.soat.mecanica.adapters.in.web.security;

import java.util.UUID;

/**
 * Principal de autenticação de um cliente, identificado pelo token emitido pela
 * Auth serverless (repo -auth). Não é um UserDetails: o cliente não tem login
 * próprio na API, só um UUID validado por assinatura, issuer e audience.
 */
public record ClientePrincipal(UUID id) {
}
