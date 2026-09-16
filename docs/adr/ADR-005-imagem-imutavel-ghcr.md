# ADR-005: Imagem multi-stage não-root no GHCR, com a tag igual ao SHA testado

- **Status:** Aceito
- **Data:** 2026-09-15 (decisão tomada na Fase 2, PRs #15, #19 e #26)
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `Dockerfile`, `.github/workflows/cd.yml` (jobs `image` e `docker`), `infra/variables.tf` (`api_image`)

## Contexto

A mesma imagem precisa rodar no Docker Compose, no Kind da pipeline e no EKS,
em contas AWS diferentes, sem que o node precise de credencial para fazer o
pull e sem que um `latest` mutável esconda qual código está em produção. O
build também não podia depender de um `.jar` gerado na máquina de quem abre
o PR.

## Decisão

- **`Dockerfile` multi-stage:** o stage `build` (`maven:3.9.9-eclipse-temurin-21`)
  compila com `mvn -B clean package -DskipTests` — os testes já rodaram na
  CI; o stage `runtime` (`eclipse-temurin:21-jre-alpine`) recebe só o `.jar`.
- **Usuário não-root fixo:** grupo e usuário `mecanica` com UID/GID `10001`,
  `USER 10001:10001`. O mesmo UID é usado nos `securityContext` dos manifests
  (`runAsNonRoot`, `fsGroup`, init container da instrumentação).
- **Tag = commit:** o CD só roda por `workflow_run` da CI concluída com
  sucesso em `main` e publica `ghcr.io/<owner>/<repo>:<head_sha>`, com o nome
  do repositório em minúsculas (o GHCR rejeita maiúsculas — PR #26). O
  checkout, o build e o `TF_VAR_api_image` usam o mesmo `head_sha`.
- **`latest` é proibido** pela validação da variável `api_image` no
  Terraform (`ghcr.io/owner/repository:tag`, sem `:latest`).
- **Pacote público:** antes de qualquer deploy, o job faz `docker logout` e
  `docker pull` anônimo da imagem; se falhar, o deploy não acontece.
- `imagePullPolicy: IfNotPresent` nos manifests, seguro porque a tag nunca é
  reutilizada.

## Consequências

**Positivas**
- Rastreabilidade total: `kubectl get deployment` mostra o SHA exato; a
  pipeline confere que a imagem no pod é a que publicou.
- Kind e EKS fazem pull sem segredo de registry; nenhum `imagePullSecret`
  nem role IAM.
- Imagem final pequena (JRE Alpine), sem Maven, sem código-fonte, sem shell
  como root; `readOnlyRootFilesystem` funciona com um `emptyDir` em `/tmp`.

**Negativas / riscos**
- O pacote público expõe o `.jar` a qualquer pessoa; não há segredo dentro
  dele (tudo entra por variável de ambiente), mas o código compilado é
  visível. Aceito para um projeto acadêmico com repositório na organização.
- Um SHA por merge acumula imagens no GHCR; não há política de retenção
  configurada.
- Build sem cache de camadas do Maven no runner: cada CD baixa as
  dependências de novo (~2–3 min).

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| Tag `latest` ou tag por versão do `pom.xml` | Mutável ou desalinhada do commit; impossível provar qual código está no pod |
| Amazon ECR | Precisa de login pelo node (role) e de repositório por conta; recriar a cada troca de conta do Learner Lab |
| Pacote privado + `imagePullSecret` | Um token do GitHub dentro do cluster; um segredo a mais para rotacionar |
| Build do `.jar` na CI e `COPY target/*.jar` | Imagem dependeria do artefato de outra etapa; o multi-stage é autocontido e reproduz localmente |
