# ADR-009: Collector e Instrumentation do OpenTelemetry no namespace da API, exportando para o Datadog

- **Status:** Aceito
- **Data:** 2026-09-15
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `k8s/collector.yaml`, `k8s/instrumentation.yaml`, `k8s/otel-rbac.yaml`, labels e anotação em `k8s/api-deployment.yaml` (PR #29)

## Contexto

O enunciado exige latência das APIs, CPU e memória do Kubernetes,
healthchecks, logs com correlação e dashboards em Datadog, New Relic ou
equivalente. O repositório #2 instala o **OpenTelemetry Operator** (chart
0.114.0) e, na sua ADR-004, deixa para a aplicação declarar o
`OpenTelemetryCollector` e a `Instrumentation`. A API é Spring Boot e o grupo
não queria instrumentar código nem trocar a imagem por uma com agente
embutido; também não há IAM Role no Learner Lab para integrações que dependam
de IRSA.

## Decisão

- **`Instrumentation` `java-instrumentation`** no namespace da API: imagem
  `autoinstrumentation-java:2.31.1`, exporter OTLP HTTP para
  `otel-daemonset-collector.<ns>.svc.cluster.local:4318`, propagadores
  `tracecontext` e `baggage`, `OTEL_METRICS_EXPORTER` e `OTEL_LOGS_EXPORTER`
  em `otlp`. O init container roda como `10001`, não-root, sem capabilities.
  O `Deployment` da API pede a injeção pela anotação
  `instrumentation.opentelemetry.io/inject-java`.
- **`OpenTelemetryCollector` `otel-daemonset`** em modo `daemonset` (uma
  réplica por node), imagem `opentelemetry-collector-contrib:0.152.0`,
  `ServiceAccount` `otel-collector` com `ClusterRole` de leitura de pods,
  namespaces, nodes, `nodes/stats` e replicasets:
  - receivers `otlp` (gRPC 4317 / HTTP 4318), `hostmetrics` (CPU, memória,
    disco, filesystem, load, rede, processos, paging do host via `/hostfs`)
    e `kubelet_stats` (nodes, pods, containers, utilização de request/limit);
  - processors `memory_limiter`, `k8sattributes` (pod, deployment, namespace,
    node e as labels `tags.datadoghq.com/env|service|version`),
    `resourcedetection`, `resource` (`deployment.environment=prd`,
    `k8s.cluster.name`) e `batch`;
  - exporter `datadog` (`site: datadoghq.com`, chave em `otel-datadog-secret`)
    e `datadog/connector` para gerar APM stats a partir dos traces;
  - pipelines `traces`, `metrics` e `logs`.
- **Tags unificadas:** o `Deployment` carrega `tags.datadoghq.com/env=prd`,
  `service=mecanica-api`, `version=1.0.0` e injeta `DD_ENV/DD_SERVICE/
  DD_VERSION` por `fieldRef`, para que APM, infraestrutura e logs se
  cruzem no Datadog.
- O mesmo trio (RBAC, Collector, Instrumentation) é aplicado no Kind pelo
  Terraform e no EKS pelo `kubectl`; nos dois casos a pipeline confere que o
  init container do agente foi injetado e que o DaemonSet concluiu o rollout.

## Consequências

**Positivas**
- Traces, métricas JVM, logs e métricas de node/pod chegam ao Datadog sem
  uma linha de código de instrumentação e com a mesma imagem de sempre.
- Correlação por `traceId` entre requisição, log e span; latência por rota
  vem do agente Java, CPU/memória vêm de `kubelet_stats` e `hostmetrics`.
- Trocar o backend é trocar o exporter no `Collector`.

**Negativas / riscos**
- O Collector roda como root e monta `/` do host (`hostmetrics`) e usa
  `insecure_skip_verify` no kubelet (certificado autoassinado do Kind). É o
  componente mais privilegiado do namespace; restrito por `ClusterRole`
  somente-leitura e requests/limits (100m/256Mi → 500m/512Mi).
- Se o Operator estiver indisponível, pods anotados podem falhar na
  criação; a pipeline espera o Operator antes de aplicar a API.
- `version=1.0.0` é fixo; a correlação deploy↔versão no Datadog depende de
  atualizar a label (questão em aberto na RFC-001).
- A API key do Datadog é um GitHub Secret deste repositório; precisa existir
  na conta final.
- Um DaemonSet por node consome recursos dos `t3.medium` compartilhados.

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| Datadog Agent (Helm/DaemonSet) | Acopla ao fornecedor e conflita com a escolha do Operator no repo #2 |
| Agente Java embutido na imagem (`-javaagent`) | Imagem deixa de ser neutra; versão do agente amarrada ao build da aplicação |
| Micrometer + OTLP no código | Invasivo; não cobre métricas de node nem logs correlacionados sem mais código |
| Collector em modo `deployment` (uma réplica) | Não coletaria `hostmetrics`/`kubelet_stats` de todos os nodes |
| Prometheus + Grafana no cluster | Consome nodes e não atende "Datadog/New Relic ou equivalente" gerenciado |
