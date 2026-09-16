# Diagrama de Sequência — Autenticação

Cobre os dois momentos do requisito de autenticação: o cliente obtém um JWT
informando o CPF (repositório #1) e depois o usa para acessar uma rota
protegida da API (repositório #4), atravessando o Traefik (repositório #2).

```mermaid
sequenceDiagram
    participant C as Cliente
    participant GW as API Gateway (Auth)
    participant L as Lambda Auth — #1
    participant SM as Secrets Manager
    participant DB as RDS PostgreSQL — #3
    participant TR as Traefik — #2
    participant API as API mecanica — #4

    C->>GW: POST /auth/cpf { cpf }
    GW->>L: evento HTTP payload 2.0 + x-correlation-id
    L->>L: normaliza e valida o CPF
    L->>SM: GetSecretValue (credenciais do banco + chave JWT)
    L->>DB: SELECT cliente por CPF
    DB-->>L: id e status

    alt cliente ausente ou status diferente de ATIVO
        L-->>GW: 401 ACCESS_DENIED
        GW-->>C: 401 + x-correlation-id
    else cliente ativo
        L->>L: assina JWT HS256 (sub=uuid, principal_type=CLIENTE, iss, aud, exp=1h)
        L-->>GW: accessToken, tokenType, expiresIn
        GW-->>C: 200 + Bearer token + x-correlation-id
    end

    C->>TR: GET /clientes/me (Authorization: Bearer accessToken)
    TR->>API: encaminha via Ingress mecanica-gateway
    API->>API: valida assinatura, iss, aud, exp e principal_type=CLIENTE
    alt token válido
        API-->>TR: 200 (dados do cliente)
    else token ausente, expirado ou de outro emissor
        API-->>TR: 401 (nunca 500)
    end
    TR-->>C: resposta
```

## Notas de leitura

- O CPF aceita 11 dígitos ou a máscara `NNN.NNN.NNN-NN`; entrada inválida
  retorna `400` antes mesmo de consultar o banco. Cliente inexistente ou
  inativo usa o mesmo `401 ACCESS_DENIED` do CPF ausente, para não revelar se
  um CPF está cadastrado.
- O cabeçalho `x-correlation-id` é gerado pela Lambda quando o cliente não o
  envia, e devolvido em toda resposta — é o identificador que atravessa o
  repositório #1. Do lado da API (repositório #4), a correlação usa
  `X-Request-Id` ([ADR-010](../adr/ADR-010-logs-estruturados-request-id.md));
  os dois ainda não estão unificados — o cliente precisaria reenviar o
  mesmo valor como `X-Request-Id` para correlacionar as duas pontas, como
  registrado na [RFC-001](../rfc/RFC-001-api-eks-learner-lab.md).
- A API nunca chama a Lambda para validar o token: a validação é local,
  por assinatura e claims (`iss`, `aud`, `principal_type`) — ver
  [ADR-003](../adr/ADR-003-jwt-dois-emissores.md). Um token malformado ou
  expirado produz `401`/`403`, nunca `500`.
- Usuários internos (`ATENDENTE`, `MECANICO`, `ALMOXARIFE`) seguem um fluxo
  paralelo, não representado aqui: `POST /auth/login` com e-mail/senha
  direto na API, sem passar pela Lambda nem pelo API Gateway da AWS.
