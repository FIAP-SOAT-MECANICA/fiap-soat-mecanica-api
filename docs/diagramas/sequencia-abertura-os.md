# Diagrama de Sequência — Abertura de Ordem de Serviço

Fluxo operacional principal da oficina: um usuário interno (`MECANICO`) abre
uma ordem de serviço já com serviços e peças, o sistema transiciona a
situação e, quando aplicável, notifica o cliente para aprovação do
orçamento.

```mermaid
sequenceDiagram
    participant M as Mecânico
    participant TR as Traefik — #2
    participant API as API mecanica — #4
    participant DB as RDS PostgreSQL — #3
    participant CLI as Cliente (e-mail)

    M->>TR: POST /auth/login { email, senha }
    TR->>API: encaminha via Ingress
    API->>DB: valida credenciais
    API-->>M: JWT interno HS256 (1h)

    M->>TR: POST /ordem-servicos/abrir (Bearer JWT interno)
    TR->>API: encaminha via Ingress
    API->>API: valida JWT interno, exige ROLE_MECANICO
    API->>DB: cria OS (situação RECEBIDA), prestações e alocações de peças
    DB-->>API: OK (estoque debitado, valor total calculado)
    API->>API: loga order_service_created { requestId, osId }
    API-->>M: 201 Created (OS + prestações + alocações)

    M->>TR: PATCH .../transicoes (RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO)
    TR->>API: encaminha
    API->>DB: atualiza situação da OS
    API->>API: loga order_service_status_changed { situação anterior, nova }

    opt notificação por e-mail habilitada (perfil local/Compose)
        API-->>CLI: e-mail com link de aprovação do orçamento
    end
    Note over API,CLI: EMAIL_NOTIFICATIONS_ENABLED=false no ambiente EKS —<br/>ver ADR-004 e ADR-007. A notificação hoje só ocorre no ambiente local.

    CLI->>TR: PATCH /ordem-servicos/{id}/aprovar-orcamento (sem token)
    TR->>API: encaminha via Ingress
    API->>API: exige situação AGUARDANDO_APROVACAO
    API->>DB: atualiza para EM_EXECUCAO
    API->>API: loga order_service_status_changed
    API-->>CLI: 200 OK

    M->>TR: PATCH .../transicoes (EM_EXECUCAO → FINALIZADA → ENTREGUE)
    TR->>API: encaminha
    API->>DB: atualiza situação
    API-->>M: 200 OK
```

## Notas de leitura

- A abertura de OS é uma operação de **usuário interno** (`ROLE_MECANICO`),
  autenticado por e-mail/senha — não usa o JWT de cliente emitido pela Auth
  serverless (ver o diagrama de
  [autenticação](sequencia-autenticacao.md)).
- Aprovar/recusar orçamento são as únicas rotas do fluxo que **não exigem
  token**: o cliente acessa por um link direto recebido por e-mail.
- **Notificação por e-mail está desabilitada no EKS.** A variável
  `EMAIL_NOTIFICATIONS_ENABLED=false` é definida explicitamente no deploy do
  cluster real ([ADR-004](../adr/ADR-004-notificacao-email-pos-commit.md) e
  [ADR-007](../adr/ADR-007-deploy-eks-kubectl-secrets-manager.md)). Isso
  significa que, no ambiente de produção do Learner Lab, o cliente não
  recebe automaticamente o link de aprovação — é uma limitação conhecida do
  grupo, não um erro deste diagrama. Ele reflete o comportamento real do
  sistema, incluindo essa lacuna.
- Cada transição de situação é logada como evento de negócio nomeado,
  correlacionado por `requestId` — ver
  [ADR-010](../adr/ADR-010-logs-estruturados-request-id.md). Com o agente
  OpenTelemetry injetado, o mesmo `traceId` aparece no log e no Datadog
  ([ADR-009](../adr/ADR-009-otel-collector-instrumentation-datadog.md)).
- Não há exclusão física: OS `FINALIZADA`/`ENTREGUE` ou com status
  `INATIVO` saem da fila operacional (`GET /ordem-servicos`), mas continuam
  persistidas e acessíveis pelos demais fluxos.
