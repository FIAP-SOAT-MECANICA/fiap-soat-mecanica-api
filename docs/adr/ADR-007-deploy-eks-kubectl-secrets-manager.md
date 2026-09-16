# ADR-007: Deploy no EKS por `kubectl apply` na pipeline, com segredos do Secrets Manager

- **Status:** Aceito
- **Data:** 2026-09-15
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `.github/workflows/cd.yml` (job `deploy-eks`), `k8s/*.yaml` (PRs #26, #28, #29)

## Contexto

O cluster (repo #2), o gateway, o Operator e o banco (repo #3) já são
gerenciados por Terraform em seus próprios repositórios, com state em S3 por
conta. Este repositório precisa apenas colocar a aplicação lá dentro, a cada
merge, sem duplicar state, sem criar IAM (proibido no Learner Lab) e sem
gravar a senha do RDS ou a chave da Auth em lugar nenhum além do Secrets
Manager de onde elas vêm.

## Decisão

O job `deploy-eks` do CD, disparado após a publicação da imagem:

1. Assume as **credenciais temporárias da sessão** do Learner Lab a partir de
   três GitHub Secrets e faz `aws eks update-kubeconfig` no cluster
   `CLUSTER_NAME` (default `mecanica`).
2. Aplica os manifests de `k8s/` com **`envsubst` + `kubectl apply`**, na
   ordem: `Namespace`; espera do Operator e CRDs; RBAC do Collector;
   `Secret` do Datadog; `OpenTelemetryCollector`; `Instrumentation`;
   `Secret` e `ConfigMap` da API; `Service`, `Deployment`, HPA; `Ingress`.
3. **Lê os segredos em tempo de deploy** com `aws secretsmanager
   get-secret-value`: `mecanica-db-credentials` (repo #3) é obrigatório;
   `fiap-soat-mecanica-auth-production-jwt` (repo #1) é best-effort. Usuário
   e senha são mascarados no log e viram `mecanica-api-secret`; host, porta e
   nome do banco viram a URL JDBC em `mecanica-api-config`. `Secret` e
   `ConfigMap` são gerados com `kubectl create … --dry-run=client -o yaml |
   kubectl apply -f -`, o que torna o step idempotente.
4. Espera o rollout, confere que o init container
   `opentelemetry-auto-instrumentation-java` foi injetado, faz `port-forward`
   no `Service` da API e no `Service` do Traefik e exige `readiness` nos dois.
5. Escreve no resumo do job os comandos de `port-forward` para a demonstração.

Configuração de runtime relevante nos manifests:

- `Deployment` com 2 réplicas, `runAsNonRoot`, `readOnlyRootFilesystem`,
  `allowPrivilegeEscalation: false`, `capabilities: drop ALL`, `seccomp
  RuntimeDefault`, `emptyDir` em `/tmp`; probes de startup, liveness e
  readiness no Actuator; requests `250m/512Mi` e limits `1000m/1024Mi`.
- HPA `autoscaling/v2` de 2 a 5 réplicas por CPU e memória a 70%.
- `EMAIL_NOTIFICATIONS_ENABLED=false` no EKS (ver ADR-004).
- Nomes de cluster, namespace, segredos e namespaces do Traefik e do Operator
  entram por *Variables* com default; nenhum ARN ou conta no código.
- `concurrency: deploy-eks` sem cancelamento, para dois merges próximos não
  aplicarem em paralelo.

## Consequências

**Positivas**
- Nenhum segredo em Git, YAML ou output: a senha do RDS existe apenas no
  Secrets Manager e no `Secret` Kubernetes do namespace.
- Sem IAM adicional e sem state próprio; o deploy funciona em qualquer conta
  em que os repos #2 e #3 tenham sido aplicados.
- Reexecutar o workflow recria tudo depois de um `destroy` do cluster.
- O pod não conhece a AWS: a mesma imagem e os mesmos manifests rodam no
  Kind com segredos de outra origem.

**Negativas / riscos**
- Sem `plan`/diff: o que muda só é visível pelo `kubectl apply`. Mitigado
  pelo job Kind, que exercita os mesmos YAMLs a cada merge.
- Credenciais da sessão expiram em ~4 h; um CD disparado com credenciais
  vencidas falha no primeiro comando AWS e precisa ser reexecutado.
- Se a Auth ainda não existir, `AUTH_JWT_SECRET` fica vazio e tokens de
  cliente são rejeitados até o próximo CD.
- A ordem entre repositórios (#2 → #3 → #4 → #1) é convenção documentada,
  não verificada automaticamente.
- Não há step de remoção do namespace; hoje a limpeza é o `destroy` do
  cluster no repo #2.

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| Terraform com provider `kubernetes` apontando para o EKS | Backend S3 e credenciais próprios; state duplicado com o repo #2; sem ganho para um único serviço |
| External Secrets Operator / Secrets Store CSI Driver | Precisam de IRSA (IAM Role) ou de credencial estática dentro do cluster |
| Senha do RDS copiada para GitHub Secret | Quebra o contrato do repo #3 (credencial só no Secrets Manager) e duplica o segredo |
| Helm chart da aplicação | Templating mais rico, mas mais um artefato para versionar; `envsubst` cobre os poucos placeholders existentes |
| Argo CD | Componente extra no cluster e fora do escopo; a reconciliação contínua não é requisito |
