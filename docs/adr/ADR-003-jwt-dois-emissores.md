# ADR-003: JWT HS256 com dois emissores — interno e Auth serverless

- **Status:** Aceito
- **Data:** 2026-09-15
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `JwtService`, `JwtAuthenticationFilter`, `ClientePrincipal`, `SecurityConfig` (PRs #27 e #30)

## Contexto

Desde a Fase 1 a API autentica usuários internos (`ATENDENTE`, `MECANICO`,
`ALMOXARIFE`) por e-mail e senha em `POST /auth/login`, emitindo um JWT cujo
`sub` é o e-mail. A Fase 3 introduz a Function do repositório #1, que
autentica **clientes** por CPF e emite um JWT próprio: `sub` é o UUID do
cliente, com `principal_type=CLIENTE`, `iss`, `aud`, `iat`, `exp` e `jti`,
assinado com uma chave HS256 guardada no Secrets Manager do repo #1. A API
precisava aceitar os dois tokens sem confundir um UUID com um e-mail, sem
consultar a Lambda em cada requisição e sem que um token malformado
derrubasse a requisição com 500 (comportamento observado antes do PR #30).

## Decisão

- **Dois pares de chave/algoritmo, ambos HS256** (jjwt 0.12.5):
  - `jwt.secret` (`JWT_SECRET`, Base64) assina e valida os tokens internos,
    com expiração de 1 hora.
  - `auth.jwt.secret` (`AUTH_JWT_SECRET`, Base64, opcional) valida os tokens
    de cliente. Se estiver vazio, tokens de cliente são simplesmente
    rejeitados — a API sobe mesmo antes da Auth existir.
- **Validação do token de cliente** exige, além da assinatura: `principal_type`
  igual a `CLIENTE`, `iss` igual a `auth.jwt.issuer` (padrão
  `fiap-soat-mecanica-auth`), `aud` contendo `auth.jwt.audience` (padrão
  `fiap-soat-mecanica-api`) e `exp` no futuro. `sub` é convertido em UUID e
  vira um `ClientePrincipal` com a autoridade `ROLE_CLIENTE`.
- **Ordem no filtro:** para cada `Authorization: Bearer`, o filtro tenta
  primeiro o token de cliente (`extractClienteId`, que nunca lança) e só
  então o fluxo interno, que carrega o `UserDetails` pelo e-mail.
- **Falha de token não é erro de servidor:** `JwtException`,
  `IllegalArgumentException` e `UsernameNotFoundException` são capturadas; a
  requisição segue sem autenticação e o Spring Security responde 401/403.
- Rotas públicas continuam explícitas em `SecurityConfig`: `POST /usuarios`,
  `/auth/**`, consulta pública por placa, aprovação/recusa de orçamento,
  Swagger e `/actuator/health/**`. Autorização por perfil fica em
  `@PreAuthorize` nos controllers; `GET /clientes/me` é a rota do perfil
  `CLIENTE`.

## Consequências

**Positivas**
- A Auth e a API compartilham apenas um segredo e um contrato de claims; não
  há chamada de rede entre elas na validação.
- Um mesmo filtro cobre os dois públicos, e o domínio não sabe que existem
  dois emissores (`CurrentUserPort` e `CurrentClientePort` são portas
  distintas).
- Token adulterado, expirado ou de outro emissor produz 401 estável, o que o
  roteiro de aceite integrado do repo #1 exige.

**Negativas / riscos**
- HS256 obriga os dois lados a possuírem a mesma chave simétrica; o segredo
  passa pela pipeline (mascarado) até virar `Secret` Kubernetes. Uma chave
  assimétrica (RS256/ES256) eliminaria isso, mas exigiria mudança no repo #1.
- Se a Auth for implantada depois da API, `AUTH_JWT_SECRET` fica vazio até o
  próximo CD; o step é best-effort e registra o aviso.
- O token interno não carrega `iss`/`aud`; um token interno não é aceito como
  cliente porque a assinatura difere, mas o inverso depende apenas da chave.
  Aceito porque as chaves são independentes e geradas fora do código.
- Não há revogação; a expiração de 1 hora limita a janela.

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| Reaproveitar o `JWT_SECRET` interno na Lambda | Um vazamento comprometeria os dois públicos; o repo #1 já gera a própria chave |
| Chamar a Lambda (introspecção) a cada requisição de cliente | Latência e dependência de rede desnecessárias para um JWT autocontido |
| Um único filtro por rota (`/clientes/**` só cliente) | O filtro por claims é mais robusto e não amarra a segurança à convenção de URL |
| Deixar exceções de JWT subirem ao `GlobalExceptionHandler` | Foi a causa dos 500; o contrato do Spring Security é "sem autenticação → 401", e o handler não deveria conhecer JWT |
