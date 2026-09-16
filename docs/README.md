# Documentação — API da Mecânica do Braia

Decisões e justificativas do repositório `fiap-soat-mecanica-api`
(repositório #4 do Tech Challenge — Fase 3: a aplicação Java/Spring Boot da
oficina). Os demais componentes têm a própria pasta `docs/` no mesmo formato:
autenticação serverless (#1, `fiap-soat-mecanica-api-auth`), cluster
Kubernetes (#2, `fiap-soat-mecanica-api-k8s`) e banco gerenciado (#3,
`fiap-soat-mecanica-api-db`). Aqui estão apenas as decisões que existem por
causa da aplicação.

## RFCs

| Documento | Assunto |
| --- | --- |
| [RFC-001](rfc/RFC-001-api-eks-learner-lab.md) | Implantar a API no EKS do AWS Academy Learner Lab: imagem imutável, pipeline, segredos, gateway, observabilidade, contrato com os repos #1, #2 e #3, alternativas, custo, validação e riscos |

## ADRs

| Documento | Decisão |
| --- | --- |
| [ADR-001](adr/ADR-001-clean-architecture-hexagonal.md) | Clean Architecture com portas e adaptadores: `domain` sem framework, casos de uso em `application`, `adapters/in` e `adapters/out` |
| [ADR-002](adr/ADR-002-flyway-dono-do-schema.md) | Flyway é o único dono do schema (`V1`–`V9`); Hibernate roda em `validate`; o banco chega vazio de qualquer origem (Compose, Kind ou RDS) |
| [ADR-003](adr/ADR-003-jwt-dois-emissores.md) | JWT HS256 com dois emissores: token interno por e-mail/senha e token de cliente da Auth serverless (`iss`, `aud`, `principal_type`); token inválido nunca gera 500 |
| [ADR-004](adr/ADR-004-notificacao-email-pos-commit.md) | Notificação por e-mail via porta de saída e adapter SMTP, disparada após o commit, sem retry, fila ou outbox; desligada fora do ambiente local |
| [ADR-005](adr/ADR-005-imagem-imutavel-ghcr.md) | Imagem multi-stage, não-root, publicada no GHCR com a tag igual ao SHA testado pela CI; `latest` é proibido e o pacote precisa ser público |
| [ADR-006](adr/ADR-006-kind-terraform-ambiente-efemero.md) | Kind + Terraform como ambiente efêmero de validação (local e na pipeline), com PostgreSQL no cluster e `destroy` sempre ao final |
| [ADR-007](adr/ADR-007-deploy-eks-kubectl-secrets-manager.md) | Deploy no EKS por `kubectl apply` na pipeline, com credenciais lidas do AWS Secrets Manager em tempo de deploy e nenhum segredo versionado |
| [ADR-008](adr/ADR-008-ingress-traefik.md) | O `Ingress` que liga o Traefik (instalado pelo repo #2) ao `Service` da API é declarado e aplicado por este repositório |
| [ADR-009](adr/ADR-009-otel-collector-instrumentation-datadog.md) | `OpenTelemetryCollector` (DaemonSet) e `Instrumentation` Java no namespace da API, exportando traces, métricas e logs para o Datadog sem alterar código ou imagem |
| [ADR-010](adr/ADR-010-logs-estruturados-request-id.md) | Logs JSON (logstash) com `requestId` propagado por `X-Request-Id`, eventos de negócio nomeados e `errorId` na resposta de erro; nenhum segredo ou dado pessoal em log |
| [ADR-011](adr/ADR-011-qualidade-cobertura-sonar-zap.md) | Portões de qualidade: JaCoCo ≥ 80% no `verify`, Testcontainers com PostgreSQL real, SonarQube na CI, ZAP dinâmico e smoke test funcional |

## Documentação de domínio e evidências

Material das fases anteriores, mantido como referência:

| Pasta | Conteúdo |
| --- | --- |
| [`0-resumo/`](0-resumo/) | Enunciado do Tech Challenge (PDF) |
| [`1-ddd/`](1-ddd/) | Linguagem ubíqua, link do board de Domain Storytelling e Event Storming no Miro, link do modelo de tabelas no Figma e o fluxo AS IS de granularidade grossa (`.egn`) |
| [`2-testes vulnerabilidades/`](2-testes%20vulnerabilidades/) | Relatório JaCoCo (PDF), visão geral do SonarQube (imagem) e relatório ZAP by Checkmarx de 2026-05-07 (HTML) |

## Diagramas da solução

Documentação arquitetural transversal, exigida pelo edital: visão de nuvem
completa (API Gateway, Auth, EKS, banco, observabilidade) e os fluxos de
autenticação e de abertura de ordem de serviço atravessando os 4
repositórios. Publicada aqui, no repositório da aplicação, para servir de
ponto único de acesso ao professor.

| Diagrama | Conteúdo |
| --- | --- |
| [Componentes](diagramas/componentes.md) | Visão da nuvem: API Gateway, Lambda de autenticação, EKS (Traefik, OpenTelemetry Operator, API), RDS, Secrets Manager e Datadog |
| [Sequência — Autenticação](diagramas/sequencia-autenticacao.md) | Cliente obtém um JWT pelo CPF e o usa em uma rota protegida |
| [Sequência — Abertura de Ordem de Serviço](diagramas/sequencia-abertura-os.md) | Mecânico abre uma OS; transição de situação; notificação e aprovação do cliente |

O diagrama de implantação específico deste repositório está na seção
[Infraestrutura local com Terraform e Kind](../README.md#infraestrutura-local-com-terraform-e-kind)
do README principal; a topologia em nuvem detalhada está na
[RFC-001](rfc/RFC-001-api-eks-learner-lab.md).

## Convenções

- **RFC** registra uma proposta com alternativas comparadas, custo e riscos —
  escrita antes ou durante a decisão.
- **ADR** registra uma decisão já tomada: contexto, decisão, consequências.
  ADRs não são editados depois de aceitos; uma mudança gera um novo ADR que
  substitui o anterior.
- Numeração sequencial por tipo. Próximos: `RFC-002`, `ADR-012`.
