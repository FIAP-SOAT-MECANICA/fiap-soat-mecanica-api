# RFC-001: Implantação da API da Mecânica do Braia no EKS do AWS Academy Learner Lab

- **Status:** Aprovado
- **Data:** 2026-09-15
- **Autora:** Karen Barcelos
- **Repositório:** fiap-soat-mecanica-api

## Resumo

Propõe-se levar a API Java/Spring Boot da Mecânica do Braia, que até a Fase 2
rodava em Docker Compose e em um cluster Kind local, para o **cluster EKS
provisionado pelo repositório #2**, consumindo o **RDS PostgreSQL do
repositório #3** e aceitando o **JWT de cliente emitido pela Function do
repositório #1**, tudo dentro das restrições do **AWS Academy Learner Lab**.
A aplicação continua sendo uma única imagem, publicada no GHCR com a tag igual
ao commit testado, e passa a ser implantada por `kubectl apply` a partir do
GitHub Actions, com segredos lidos do AWS Secrets Manager em tempo de deploy.
Ela é exposta atrás do Traefik (gateway do cluster) e instrumentada
automaticamente pelo OpenTelemetry Operator, exportando traces, métricas e
logs para o Datadog. Esta RFC registra as restrições, o contrato com os demais
repositórios, as alternativas avaliadas, o custo, a validação e os riscos.

## Motivação

A Fase 3 exige que a aplicação rode em Kubernetes com escalabilidade, atrás de
um API Gateway, com autenticação de cliente por CPF via Function Serverless,
banco gerenciado, infraestrutura como código, CI/CD e observabilidade
(latência, CPU/memória, healthchecks, logs estruturados com correlação e
dashboards). A solução foi dividida em quatro repositórios; este é o único
que contém código de negócio. Restava definir **como** a aplicação se encaixa
nos outros três sem duplicar responsabilidade (a rede, o gateway, o Operator e
o banco já existem lá) e sem introduzir segredo em código, imagem mutável ou
passo manual no deploy.

## Restrições do Learner Lab que moldaram a proposta

| Restrição | Impacto |
| --- | --- |
| Não é possível criar IAM Roles, políticas ou OIDC providers | Sem IRSA para a API ler o Secrets Manager; a pipeline lê os segredos com as credenciais da sessão e os entrega ao pod como `Secret` Kubernetes |
| Credenciais temporárias (~4h): access key + secret + session token | Os três valores ficam em GitHub Secrets e precisam ser renovados antes de cada deploy real |
| Crédito de ~US$ 50 por conta, dois nodes `t3.medium` compartilhados com Traefik e Operator | Requests/limits enxutos (250m/512Mi → 1000m/1024Mi); HPA entre 2 e 5 réplicas |
| Recursos persistem após a sessão; cluster e banco são destruídos com frequência | Deploy idempotente (`kubectl apply` com `--dry-run=client` para Secret/ConfigMap); reexecutar o CD basta para recriar tudo |
| Nenhum endpoint público estável (gateway em `NodePort`, IP do node muda) | A validação da pipeline e a demonstração usam `kubectl port-forward` |
| Quatro contas durante o desenvolvimento, uma na entrega | Nomes de cluster, namespace e segredos entram por *Variables* com default; nenhum ARN ou account ID no código |

## Proposta

### 1. Imagem imutável por commit

- `Dockerfile` multi-stage: `maven:3.9.9-eclipse-temurin-21` compila; a
  imagem final é `eclipse-temurin:21-jre-alpine` com usuário `10001`
  não-root.
- O CD publica `ghcr.io/<owner-minúsculo>/<repo>:<head_sha>` usando
  exatamente o `workflow_run.head_sha` que a CI aprovou. `latest` é rejeitado
  por validação de variável no Terraform.
- O pacote no GHCR é público; a pipeline faz um `docker pull` anônimo antes de
  qualquer deploy para provar isso.
- Ver [ADR-005](../adr/ADR-005-imagem-imutavel-ghcr.md).

### 2. Pipeline em dois estágios

| Workflow | Gatilho | O que faz |
| --- | --- | --- |
| `ci.yml` | Push em `main`, `feat/**`, `feature/**`; PR para `main`/`develop` | `./mvnw clean verify` (testes, JaCoCo ≥ 80%), upload do relatório, SonarQube quando configurado |
| `cd.yml` | `workflow_run` da CI concluída com sucesso em `main` | Job `docker` publica a imagem; em paralelo, job `deploy` valida em Kind efêmero e job `deploy-eks` implanta no cluster real |

O job Kind continua existindo como **validação reproduzível** de manifests,
migrations, HPA e instrumentação em um cluster que nasce e morre na própria
execução (ver [ADR-006](../adr/ADR-006-kind-terraform-ambiente-efemero.md)).
O job EKS é o deploy de produção (ver
[ADR-007](../adr/ADR-007-deploy-eks-kubectl-secrets-manager.md)).

### 3. Deploy no EKS por `kubectl apply`

Este repositório **não roda Terraform contra a AWS**. Os manifests de `k8s/`
usam placeholders `${...}` resolvidos por `envsubst`, e o job `deploy-eks`
aplica, nesta ordem:

1. `Namespace` `mecanica`.
2. Espera o OpenTelemetry Operator (instalado pelo repo #2) ficar `Available`
   e os CRDs `Established`.
3. RBAC do Collector, `Secret` com a API key do Datadog,
   `OpenTelemetryCollector` e `Instrumentation`.
4. Leitura do secret `mecanica-db-credentials` (repo #3) e, em modo
   best-effort, do secret JWT da Auth (`fiap-soat-mecanica-auth-production-jwt`,
   repo #1); geração dos objetos `Secret` (`mecanica-api-secret`) e
   `ConfigMap` (`mecanica-api-config`, com a URL JDBC e e-mail desligado).
5. `Service`, `Deployment`, `HorizontalPodAutoscaler` e `Ingress` da API.
6. Verificações: rollout concluído, init container do agente Java injetado,
   `readiness` via `port-forward` direto no `Service` e, depois, via
   `port-forward` no `Service` do Traefik.

### 4. Segredos

| Segredo | Origem | Como chega ao pod |
| --- | --- | --- |
| Usuário e senha do RDS | Secrets Manager `mecanica-db-credentials` (repo #3) | `mecanica-api-secret` → `SPRING_DATASOURCE_USERNAME/PASSWORD` |
| Chave HS256 dos tokens internos | GitHub Secret `JWT_SECRET` (Base64) | `mecanica-api-secret` → `JWT_SECRET` |
| Chave HS256 dos tokens de cliente | Secrets Manager do repo #1, campo `secret` | `mecanica-api-secret` → `AUTH_JWT_SECRET` (vazio se a Auth ainda não foi implantada) |
| API key do Datadog | GitHub Secret `DATADOG_API_KEY` | `otel-datadog-secret` → `DD_API_KEY` no Collector |
| Credenciais AWS da sessão | GitHub Secrets (3 valores) | Só no runner; nunca no cluster |

A pipeline mascara usuário e senha com `::add-mask::` e, no job Kind, falha se
qualquer valor de segredo aparecer nos logs da API ou do banco.

### 5. Gateway

A API é publicada por um `Ingress` de classe `traefik`, rota `/` para o
`Service` `mecanica-api:8080`, no entrypoint `web`. O Traefik em si é do repo
#2; este repositório só declara o roteamento (ver
[ADR-008](../adr/ADR-008-ingress-traefik.md)).

### 6. Autenticação

- Usuários internos (`ATENDENTE`, `MECANICO`, `ALMOXARIFE`): login por
  e-mail/senha em `POST /auth/login`, JWT HS256 de 1 hora assinado com
  `JWT_SECRET`.
- Clientes: JWT emitido pela Function do repo #1, validado com
  `AUTH_JWT_SECRET`, `iss = fiap-soat-mecanica-auth`,
  `aud = fiap-soat-mecanica-api` e `principal_type = CLIENTE`; `sub` é o UUID
  do cliente. `GET /clientes/me` é a rota de cliente.
- O filtro tenta o token de cliente antes do interno e nunca converte um
  token inválido em 500 (ver [ADR-003](../adr/ADR-003-jwt-dois-emissores.md)).

### 7. Observabilidade

- **Traces, métricas e logs da JVM:** agente Java injetado pelo Operator
  (`Instrumentation`), exportando por OTLP para um `OpenTelemetryCollector`
  em modo `daemonset` no namespace da API.
- **Métricas de infraestrutura:** receivers `hostmetrics` (CPU, memória,
  disco, rede do node) e `kubelet_stats` (nodes, pods, containers, utilização
  de request/limit).
- **Destino:** exporter `datadog` com `datadog/connector` para APM stats; tags
  unificadas `env/service/version` via labels `tags.datadoghq.com/*` e
  processor `k8sattributes`.
- **Logs da aplicação:** JSON logstash com `requestId`, `event`, `route`,
  `status`, `durationMs` e eventos de negócio da ordem de serviço
  (ver [ADR-009](../adr/ADR-009-otel-collector-instrumentation-datadog.md) e
  [ADR-010](../adr/ADR-010-logs-estruturados-request-id.md)).
- **Healthchecks:** `startup`, `liveness` e `readiness` do Actuator; a
  pipeline exige readiness antes de considerar o deploy concluído.

### 8. Escalabilidade

`Deployment` com 2 réplicas e HPA `autoscaling/v2` entre 2 e 5 réplicas, alvo
de 70% de CPU e 70% de memória, alimentado pelo `metrics-server` que o repo
#2 instala como addon do EKS (e que o Terraform do Kind instala localmente).

## Contrato com os demais repositórios

| Repositório | O que este consome dele | O que ele consome deste |
| --- | --- | --- |
| #1 Auth (`fiap-soat-mecanica-api-auth`) | Secret JWT (`jwt_secret_arn`, campo `secret`, Base64) e o contrato de claims do token de cliente | Tabela `clientes` criada pelas migrations desta aplicação, lida por SQL |
| #2 Kubernetes (`fiap-soat-mecanica-api-k8s`) | Cluster `mecanica`, `metrics-server`, Traefik (namespace `traefik`, classe `traefik`) e OpenTelemetry Operator (namespace `opentelemetry-system`) | `Ingress`, `OpenTelemetryCollector` e `Instrumentation` declarados aqui, no namespace `mecanica` |
| #3 Banco (`fiap-soat-mecanica-api-db`) | Secret `mecanica-db-credentials` (`host`, `port`, `dbname`, `username`, `password`) e conectividade na porta 5432 a partir do SG do cluster | Schema e dados iniciais via Flyway `V1`–`V9` na subida da API |

Ordem de aplicação: #2 → #3 → #4 (este) → #1. Se o #1 for implantado depois
deste, o CD precisa ser reexecutado para que `AUTH_JWT_SECRET` deixe de ser
vazio.

## Alternativas avaliadas

### Mecanismo de deploy no EKS

| Opção | Prós | Contras | Decisão |
| --- | --- | --- | --- |
| **`kubectl apply` + `envsubst` na pipeline** | Reaproveita os mesmos YAMLs do Kind; sem state; idempotente; nenhuma dependência de IAM | Sem `plan`/diff antes de aplicar; ordem garantida só pela sequência de steps | **Escolhido** |
| Terraform com providers `kubernetes`/`kubectl` (como no Kind) | `plan` e state | Precisaria de backend S3 próprio e de uma segunda cadeia de credenciais; duplicaria o state do repo #2 | Rejeitado |
| Helm chart próprio | Templating e versionamento do release | Mais um artefato para manter; ganho pequeno para um único serviço | Rejeitado |
| Argo CD / GitOps | Reconciliação contínua | Componente extra no cluster com crédito limitado; fora do escopo do enunciado | Rejeitado |

### Registro de imagens

| Opção | Decisão | Motivo |
| --- | --- | --- |
| **GHCR público, tag = SHA** | **Escolhido** | Gratuito, sem IAM, funciona em Kind e EKS com pull anônimo; imutabilidade auditável |
| Amazon ECR | Rejeitado | Exigiria login do node (role) e repositório por conta; recriar a cada troca de conta |
| Docker Hub | Rejeitado | Limite de pull anônimo; sem integração com o repositório |

### Entrega de segredos ao pod

| Opção | Decisão | Motivo |
| --- | --- | --- |
| **Pipeline lê o Secrets Manager e cria `Secret` Kubernetes** | **Escolhido** | Sem IAM adicional; o pod não conhece a AWS; funciona igual no Kind |
| External Secrets Operator / Secrets Store CSI | Rejeitado | Ambos precisam de IRSA ou credenciais estáticas no cluster |
| Senha do banco em GitHub Secret | Rejeitado | Duplicaria o segredo que o repo #3 já gera e guarda; quebraria o contrato "nenhum output expõe a credencial" |

### Banco de dados em produção

| Opção | Decisão | Motivo |
| --- | --- | --- |
| **RDS do repo #3; PostgreSQL em pod só no Kind** | **Escolhido** | Requisito de banco gerenciado; o pod local mantém a validação da pipeline independente da AWS |
| PostgreSQL em `Deployment` + PVC também no EKS | Rejeitado | Não é gerenciado; PVC em EKS exige CSI driver com IAM |

### Instrumentação

| Opção | Decisão | Motivo |
| --- | --- | --- |
| **Auto-instrumentação Java pelo Operator** | **Escolhido** | Zero mudança de código ou imagem; correlação traces/logs por `traceId` |
| Datadog Agent como DaemonSet | Rejeitado | Acopla o cluster ao fornecedor; o repo #2 já escolheu o Operator |
| Micrometer + exporters no código | Rejeitado | Invasivo; a tela de infraestrutura ficaria fora |

## Custo estimado

A aplicação **não cria recursos pagos na AWS**: usa nodes do repo #2
(~US$ 4,70/dia) e o RDS do repo #3 (~US$ 0,46/dia). Itens próprios:

| Item | Custo |
| --- | --- |
| GHCR (pacote público) | Sem custo |
| GitHub Actions (runners hospedados) | Dentro da franquia do plano; o job Kind leva ~10 min por execução |
| Datadog | Conta de avaliação do grupo; sem custo no período da entrega |
| Secrets Manager | Já contabilizado nos repos #1 e #3 |

## Validação

- **A cada PR e push:** `mvn clean verify` com JaCoCo ≥ 80%; testes de
  integração com PostgreSQL real via Testcontainers.
- **A cada merge em `main` (job Kind):** cluster criado do zero, Flyway aplica
  ou valida exatamente 9 migrations, imagem no pod igual à publicada, HPA com
  duas métricas reportando, agente Java injetado, Collector em rollout, smoke
  test funcional (`tests/api-smoke/api-smoke.mjs`), segredos ausentes dos
  logs, `terraform destroy` e checagem de que nenhum container Kind sobrou.
- **A cada merge em `main` (job EKS):** Operator disponível, rollout concluído,
  init container `opentelemetry-auto-instrumentation-java` presente,
  readiness respondendo direto e através do Traefik; o resumo do job imprime
  os comandos de `port-forward` para a demonstração.
- **Manual, na demo:** login interno, abertura consolidada de OS, transições,
  `POST /auth/cpf` no repo #1 seguido de `GET /clientes/me` aqui, traces e
  métricas de node no Datadog.

## Riscos e mitigações

| Risco | Mitigação |
| --- | --- |
| Credenciais do Learner Lab expiram durante o CD | Renovar os três Secrets antes do merge ou reexecutar o workflow; o deploy é idempotente |
| Auth implantada depois desta API: `AUTH_JWT_SECRET` vazio e tokens de cliente rejeitados | O step é best-effort e avisa no log; reexecutar o CD resolve |
| `V9__mock_dados.sql` cria usuários com senha conhecida também no RDS | Aceito para a demonstração; registrado como questão em aberto |
| Collector roda como root e usa `insecure_skip_verify` no kubelet | Necessário para `hostmetrics` e para o certificado autoassinado do Kind; restrito ao namespace da API |
| `NodePort` 30080 continua no `Service` da API mesmo com o gateway | O SG dos nodes não libera a porta; o acesso previsto é pelo Traefik |
| Dois pods de 512Mi de request mais Collector por node em `t3.medium` | Limites definidos; HPA não passa de 5; o repo #2 escala nodes até 3 |
| Notificações por e-mail desligadas no EKS (sem SMTP) | Funcionalidade demonstrada localmente com MailHog; ver ADR-004 |

## Questões em aberto

- O README principal ainda descreve o projeto como "Tech Challenge - Fase 2";
  atualizar para a Fase 3 com o diagrama da topologia em nuvem.
- Proteger a migration de dados mock por perfil (ou removê-la do caminho de
  produção) para não semear credenciais conhecidas no RDS.
- `tags.datadoghq.com/version` está fixo em `1.0.0`; usar o SHA da imagem
  daria correlação direta entre deploy e versão no Datadog.
- Trocar o `Service` da API para `ClusterIP` no EKS, deixando `NodePort` só
  no Kind, onde o `extraPortMappings` depende dele.
- Propagar o `x-correlation-id` recebido da Auth (hoje a API gera/aceita
  apenas `X-Request-Id`).
- O job Kind e o job EKS rodam em paralelo; avaliar se o EKS deve depender do
  Kind, ao custo de ~10 min a mais por deploy.
- Não há workflow de remoção do namespace no EKS; hoje o cluster inteiro é
  destruído pelo repo #2.
