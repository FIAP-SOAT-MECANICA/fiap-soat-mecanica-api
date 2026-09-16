# ADR-011: Portões de qualidade — JaCoCo 80%, Testcontainers, SonarQube, ZAP e smoke test

- **Status:** Aceito
- **Data:** 2026-09-15 (decisões tomadas nas Fases 1 e 2, PRs #12, #14 e #15)
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `pom.xml` (plugins `jacoco` e `sonar`), `src/test/.../support/PostgresIntegrationTest`, `tests/api-smoke/api-smoke.mjs`, `.github/workflows/ci.yml`, `docs/2-testes vulnerabilidades/`

## Contexto

A Fase 2 exigiu cobertura de testes, análise estática e verificação de
vulnerabilidades; a Fase 3 acrescentou pipeline obrigatória. O grupo queria
que "quebrou a regra de negócio", "quebrou o schema" e "abriu uma rota sem
autorização" fossem detectados antes do merge, e que a evidência dessas
análises ficasse versionada.

## Decisão

- **Cobertura mínima de 80%** (`COVEREDRATIO`, JaCoCo 0.8.12) verificada em
  `mvn verify`; a CI falha abaixo disso e publica o relatório como artefato.
  O relatório em PDF e a visão do SonarQube da Fase 2 estão em
  `docs/2-testes vulnerabilidades/`.
- **PostgreSQL real nos testes de integração:** a classe de suporte
  `PostgresIntegrationTest` sobe um container com Testcontainers e injeta
  URL, usuário, senha e driver; Flyway e `ddl-auto=validate` rodam iguais à
  produção. Testes de domínio, casos de uso, mappers e controllers
  continuam unitários (Mockito/MockMvc), sem container.
- **Perfil `test`** desliga a notificação por e-mail e usa um `jwt.secret`
  fixo de teste; `JavaMailSender` é mockado no teste do adapter.
- **SonarQube** na CI quando `SONAR_TOKEN` e `SONAR_HOST_URL` existem;
  localmente o Compose sobe `sonarqube:community` com banco próprio.
- **ZAP by Checkmarx** (análise dinâmica) executado contra a API em execução
  com token JWT de cada perfil; o relatório HTML de 2026-05-07 é a evidência
  versionada.
- **Smoke test funcional** em Node (`tests/api-smoke/api-smoke.mjs`) cobre o
  fluxo completo (login, cadastros, abertura de OS, transições) e roda na
  pipeline contra o Kind, além de localmente por `API_BASE_URL`.

## Consequências

**Positivas**
- O `verify` local é o mesmo da CI; ninguém descobre cobertura baixa depois
  do push.
- Migrations, mapeamentos JPA e consultas paginadas são exercitados no
  mesmo engine e versão de produção.
- A evidência das análises (JaCoCo, Sonar, ZAP) é parte do repositório, como
  o enunciado pede.

**Negativas / riscos**
- Testcontainers exige Docker na máquina e no runner; a suíte completa leva
  mais tempo do que com banco em memória.
- 80% é um limiar global; cobertura pode se concentrar em mappers e DTOs.
  Mitigado pela revisão de PR e pelo Sonar.
- O relatório ZAP é uma fotografia da Fase 2; não há execução automática do
  ZAP na pipeline.
- A análise Sonar depende de um servidor externo configurado por *Variables*
  e *Secrets*; sem eles o step é pulado silenciosamente.

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| H2 nos testes | Dialeto diferente; Flyway e `validate` não seriam testados de verdade (ver ADR-002) |
| Cobertura por classe/pacote em vez de global | Mais preciso, porém mais atrito para um projeto acadêmico; o global cumpre o requisito |
| ZAP como job da pipeline | Tempo de execução alto e alvo que precisa estar de pé com tokens de três perfis; ficou como execução manual documentada |
| Smoke test em JUnit | O script Node roda contra qualquer URL (Compose, Kind, `port-forward` do EKS) sem compilar o projeto |
