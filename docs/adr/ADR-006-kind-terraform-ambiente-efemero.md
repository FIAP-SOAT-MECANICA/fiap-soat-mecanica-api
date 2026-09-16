# ADR-006: Kind + Terraform como ambiente efêmero de validação

- **Status:** Aceito
- **Data:** 2026-09-15 (decisão tomada na Fase 2, PR #24; ampliada na Fase 3, PR #29)
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `infra/*.tf`, `k8s/*.yaml`, `.github/workflows/cd.yml` (job `deploy`)

## Contexto

Na Fase 2 o requisito era subir a aplicação em Kubernetes com HPA, provisionado
por Terraform, sem depender de nuvem. Na Fase 3 o cluster real passou a ser o
EKS do repositório #2, mas o grupo ainda precisava de um lugar para provar,
a cada merge, que manifests, migrations, HPA, Collector e instrumentação
funcionam juntos — sem gastar crédito do Learner Lab e sem depender de
credenciais que expiram em quatro horas.

## Decisão

Manter o **Kind** como ambiente efêmero, criado e destruído pelo Terraform:

- `kind_cluster` (provider `tehcyx/kind` 0.11.0) com um control-plane,
  imagem `kindest/node:v1.34.3` fixada por digest e `extraPortMappings` de
  `127.0.0.1:8080` para o `NodePort` `30080` da API.
- Providers `gavinbunney/kubectl` 1.19.0 e `hashicorp/helm` 3.0.2,
  autenticados pelos certificados que o próprio `kind_cluster` devolve;
  Terraform `>= 1.14.5, < 1.16.0`, lockfile versionado.
- Os YAMLs de `k8s/` são lidos por `kubectl_path_documents` com os mesmos
  placeholders que o `envsubst` usa no EKS, e classificados por `locals` em
  etapas com `depends_on` explícito: namespace → RBAC do OTel → metrics-server
  → PostgreSQL (PVC, Service, Deployment) → Secrets → API (ConfigMap, Service,
  Deployment) → HPA → Collector → Instrumentation. Um bloco `check` falha o
  `plan` se algum documento YAML ficar fora de todas as etapas.
- **Somente no Kind** existe PostgreSQL em pod (`postgres:17` + PVC); no EKS o
  banco é o RDS do repo #3.
- O OpenTelemetry Operator (chart 0.114.0) é instalado pelo Terraform daqui,
  espelhando o que o repo #2 faz no EKS, para que o mesmo `Instrumentation`
  seja exercitado.
- `Secret`s são gerados por `yamlencode` a partir de variáveis `sensitive`;
  nenhum valor real fica nos YAMLs.
- Na pipeline o cluster vive só durante o job: `terraform destroy` roda em
  `always()` e um step final falha se algum container Kind sobrar.

## Consequências

**Positivas**
- Cada merge em `main` prova, de graça e em ~10 min, que a imagem publicada
  sobe, aplica 9 migrations, fica Ready, escala por HPA e recebe o agente
  Java — antes ou em paralelo ao deploy real.
- O mesmo conjunto de YAMLs serve os dois ambientes; divergência entre Kind e
  EKS aparece no PR.
- Desenvolvedor reproduz o ambiente completo localmente com `terraform apply`.

**Negativas / riscos**
- Duas formas de aplicar os manifests (Terraform no Kind, `kubectl` no EKS).
  O `check` e os placeholders comuns reduzem o risco de drift, mas ele
  existe.
- O state local do Kind contém os segredos em texto; o arquivo é ignorado
  pelo Git e o runner é efêmero, mas localmente exige cuidado.
- O job Kind e o job EKS rodam em paralelo; hoje o EKS não espera o Kind
  (registrado na RFC-001).
- Kind roda em um único node; HPA e `kubelet_stats` são validados, mas não
  o comportamento multi-node.

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| Minikube / k3d | Equivalentes; Kind tem provider Terraform maduro e imagem fixável por digest |
| Deixar de ter ambiente local após o EKS | Perderia a validação por merge sem custo e a reprodução offline |
| Terraform também para aplicar no EKS | Exigiria backend S3 e credenciais próprias neste repo (ver ADR-007) |
| `kubectl apply` também no Kind | O Terraform garante ordem, espera de rollout e destruição completa em um comando |
