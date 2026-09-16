# Diagramas da solução

Documentação arquitetural **transversal**, exigida pelo edital do Tech
Challenge — Fase 3: visão de todo o sistema, não de um repositório isolado.
As decisões que originam cada elemento aqui desenhado estão detalhadas nas
ADRs e RFCs de cada repositório (linkadas em cada diagrama).

| Diagrama | Conteúdo |
| --- | --- |
| [Componentes](componentes.md) | Visão da nuvem: API Gateway, Lambda de autenticação, EKS (Traefik, OpenTelemetry Operator, API), RDS, Secrets Manager e Datadog |
| [Sequência — Autenticação](sequencia-autenticacao.md) | Cliente obtém um JWT pelo CPF e o usa em uma rota protegida da API |
| [Sequência — Abertura de Ordem de Serviço](sequencia-abertura-os.md) | Mecânico autenticado abre uma OS; transição de situação; notificação e aprovação do cliente |

## Repositórios da solução

| # | Repositório | Papel |
| --- | --- | --- |
| 1 | [fiap-soat-mecanica-api-auth](https://github.com/FIAP-SOAT-MECANICA/fiap-soat-mecanica-api-auth) | Function Serverless de autenticação por CPF (Lambda + API Gateway) |
| 2 | [fiap-soat-mecanica-api-k8s](https://github.com/FIAP-SOAT-MECANICA/fiap-soat-mecanica-api-k8s) | Cluster EKS, Traefik (gateway) e OpenTelemetry Operator |
| 3 | [fiap-soat-mecanica-api-db](https://github.com/FIAP-SOAT-MECANICA/fiap-soat-mecanica-api-db) | Banco de dados gerenciado (RDS PostgreSQL) |
| 4 | [fiap-soat-mecanica-api](https://github.com/FIAP-SOAT-MECANICA/fiap-soat-mecanica-api) | Aplicação principal (este repositório) |
