# Diagrama de sequência — autenticação

Fluxos de obtenção e validação de token da Mecânica do Braia, cobrindo os
quatro repositórios: a Function serverless (`fiap-soat-mecanica-api-auth`),
o cluster e o gateway (`fiap-soat-mecanica-api-k8s`), o banco gerenciado
(`fiap-soat-mecanica-api-db`) e esta API. As classes citadas são as reais;
os status HTTP vêm do `AuthHandler` (Lambda) e do `GlobalExceptionHandler`
(API).

Decisões relacionadas: [ADR-003](../adr/ADR-003-jwt-dois-emissores.md),
[ADR-008](../adr/ADR-008-ingress-traefik.md) e o contrato de integração no
repositório `-auth`.

## 1. Cliente obtém token por CPF (Auth serverless)

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant GW as Amazon API Gateway (HTTP API)
    participant Lambda as AuthHandler (Lambda, repo -auth)
    participant SM as AWS Secrets Manager
    participant RDS as PostgreSQL (RDS, repo -db)

    Cliente->>GW: POST /auth/cpf (cpf) [x-correlation-id opcional]
    GW->>Lambda: evento APIGatewayV2HTTPEvent
    Lambda->>Lambda: resolve correlationId (header, requestId do gateway ou awsRequestId)
    Lambda->>Lambda: valida corpo (somente campo cpf) e dígitos do CPF

    alt corpo ou CPF inválido
        Lambda-->>GW: 400 INVALID_REQUEST / INVALID_CPF
        GW-->>Cliente: 400 + x-correlation-id
    end

    Lambda->>SM: GetSecretValue(DB_SECRET_ARN) e GetSecretValue(JWT_SECRET_ARN)
    Note right of Lambda: AuthServiceFactory guarda o resultado por 5 min
    SM-->>Lambda: host, port, dbname, username, password / chave HS256 (Base64)

    Lambda->>RDS: SELECT id, status FROM clientes WHERE cpf = ? (timeout 3 s)
    RDS-->>Lambda: linha ou vazio

    alt cliente inexistente ou status diferente de ATIVO
        Lambda-->>GW: 401 ACCESS_DENIED (mesma resposta nos dois casos)
        GW-->>Cliente: 401 + x-correlation-id
    else erro de conexão ou SQL
        Lambda-->>GW: 503 CUSTOMER_DIRECTORY_UNAVAILABLE
        GW-->>Cliente: 503 + x-correlation-id
    else cliente ATIVO
        Lambda->>Lambda: JwtTokenIssuer assina HS256 (iss, sub=UUID do cliente, aud, jti, iat, exp, principal_type=CLIENTE)
        Lambda-->>GW: 200 accessToken, tokenType=Bearer, expiresIn
        GW-->>Cliente: 200 + x-correlation-id
    end
```

O `sub` do token é o UUID de `clientes.id`, nunca o CPF. A Lambda só faz
`SELECT`; a tabela `clientes` é criada pelas migrations desta API
([ADR-002](../adr/ADR-002-flyway-dono-do-schema.md)).

## 2. Usuário interno obtém token por e-mail e senha

```mermaid
sequenceDiagram
    autonumber
    actor Mecanico as Mecânico / Atendente / Almoxarife
    participant Traefik as Traefik (gateway, repo -k8s)
    participant API as API (AuthController)
    participant UC as AutenticarUsuarioUseCase
    participant DB as PostgreSQL (RDS)
    participant JWT as JwtService

    Mecanico->>Traefik: POST /auth/login (email, senha)
    Traefik->>API: Ingress mecanica-gateway → Service mecanica-api:8080
    Note over API: rota pública em SecurityConfig, HttpRequestLoggingFilter gera/propaga X-Request-Id
    API->>UC: login(email, senha)
    UC->>DB: SELECT usuarios WHERE email = ?
    DB-->>UC: usuário (senha com hash BCrypt)

    alt usuário não encontrado
        UC-->>API: RecursoNaoEncontradoException
        API-->>Mecanico: 404 + errorId
    else senha não confere
        UC-->>API: SenhaInvalidaException
        API-->>Mecanico: 401 + errorId
    else credenciais válidas
        UC->>JWT: gerarToken(email)
        JWT-->>UC: JWT HS256 com JWT_SECRET, sub=email, exp = 1 h
        UC-->>API: token
        API-->>Traefik: 200 (token)
        Traefik-->>Mecanico: 200 (token)
    end
```

## 3. Validação do `Authorization: Bearer` em qualquer rota protegida

O mesmo filtro atende os dois públicos. Ele tenta primeiro o token de
cliente e depois o interno; nenhuma falha de token vira 500.

```mermaid
sequenceDiagram
    autonumber
    actor Chamador as Cliente ou usuário interno
    participant Traefik as Traefik (gateway)
    participant Log as HttpRequestLoggingFilter
    participant Filtro as JwtAuthenticationFilter
    participant JWT as JwtService
    participant UDS as CustomUserDetailsService
    participant DB as PostgreSQL (RDS)
    participant Ctrl as Controller (@PreAuthorize)

    Chamador->>Traefik: requisição com Authorization: Bearer token
    Traefik->>Log: roteia para a API
    Log->>Log: X-Request-Id válido? senão gera UUID, grava requestId no MDC
    Log->>Filtro: segue a cadeia

    Filtro->>JWT: extractClienteId(token)
    alt AUTH_JWT_SECRET configurado e assinatura confere
        JWT->>JWT: confere principal_type=CLIENTE, iss, aud e exp
        JWT-->>Filtro: Optional(UUID do cliente)
        Filtro->>Filtro: ClientePrincipal + ROLE_CLIENTE no SecurityContext
    else não é token de cliente (chave ausente, assinatura, claims ou expirado)
        JWT-->>Filtro: Optional vazio (nunca lança)
        Filtro->>JWT: extractUsername(token) com JWT_SECRET
        alt token interno válido
            JWT-->>Filtro: email
            Filtro->>UDS: loadUserByUsername(email)
            UDS->>DB: SELECT usuarios WHERE email = ?
            DB-->>UDS: usuário e cargo
            UDS-->>Filtro: UserDetails (ROLE_MECANICO, ROLE_ATENDENTE ou ROLE_ALMOXARIFE)
            Filtro->>JWT: isTokenValid(token, user)
            Filtro->>Filtro: autenticação no SecurityContext
        else JwtException ou usuário inexistente
            Filtro->>Filtro: captura a exceção e segue sem autenticar
        end
    end

    Filtro->>Ctrl: segue a cadeia
    alt sem autenticação ou sem a role exigida
        Ctrl-->>Chamador: 401 / 403 (Spring Security), sem 500
    else autorizado
        Ctrl->>Ctrl: executa o caso de uso (ex.: GET /clientes/me lê o cliente pelo UUID do token)
        Ctrl-->>Chamador: 2xx + X-Request-Id
    end
    Log->>Log: loga http_request_completed (method, route, status, durationMs)
```

Observação: a API devolve `X-Request-Id`, e a Lambda devolve
`x-correlation-id`. Para correlacionar as duas chamadas no Datadog, o cliente
precisa reenviar o valor recebido da Auth como `X-Request-Id` (pendência
registrada na [RFC-001](../rfc/RFC-001-api-eks-learner-lab.md)).
