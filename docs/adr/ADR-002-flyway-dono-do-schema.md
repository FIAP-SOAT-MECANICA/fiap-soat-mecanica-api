# ADR-002: Flyway é o único dono do schema; Hibernate apenas valida

- **Status:** Aceito
- **Data:** 2026-09-15 (decisão tomada na Fase 1, reforçada na Fase 3)
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `src/main/resources/db/migration/V1__…V9__`, `application.properties`

## Contexto

O banco da aplicação muda de origem conforme o ambiente: PostgreSQL 17 em
Docker Compose, em um `Deployment` com PVC no Kind, e uma instância RDS
entregue **vazia** pelo repositório #3. A Function do repositório #1 também
lê a tabela `clientes` diretamente por SQL. É preciso que a estrutura seja
idêntica em todos os lugares, reproduzível a cada `destroy`/`apply` e
auditável no Git — sem que ninguém rode DDL à mão e sem que o Hibernate
altere tabelas em produção.

## Decisão

- **Flyway** (`flyway-core` + `flyway-database-postgresql`) aplica as
  migrations `V1__create_usuario` a `V8__create_alocacao_pecas` (estrutura) e
  `V9__mock_dados` (usuários e massa de teste) na subida da API, em qualquer
  ambiente.
- **Hibernate roda com `ddl-auto=validate`**: se o modelo JPA divergir do
  schema, a aplicação não sobe. `open-in-view` fica desligado.
- Este repositório é o **único** que cria ou altera tabelas; os repos #1 e
  #3 declaram isso explicitamente nas suas docs.
- A pipeline (job Kind) confirma nos logs que o Flyway aplicou ou validou
  exatamente 9 migrations antes de considerar o deploy válido.
- Os testes de integração rodam as mesmas migrations em um PostgreSQL de
  Testcontainers (ver ADR-011), e não em H2.

## Consequências

**Positivas**
- Um único artefato (a imagem da API) carrega código e schema compatíveis;
  não há passo manual de banco em nenhum ambiente.
- `validate` transforma divergência de modelo em falha de startup, visível na
  readiness e na pipeline, e nunca em `ALTER TABLE` silencioso.
- O contrato com a Lambda de autenticação (colunas de `clientes`) é versionado
  aqui.

**Negativas / riscos**
- `V9__mock_dados.sql` cria usuários com senha conhecida também no RDS de
  produção. Aceito para a demonstração acadêmica e registrado como questão
  em aberto na RFC-001.
- Migrations são imutáveis depois de aplicadas; corrigir um erro exige nova
  versão. Em ambientes descartáveis isso é indolor, mas o RDS persistente
  precisa da mesma disciplina.
- Mudança de coluna em `clientes` pode quebrar a consulta SQL do repo #1 sem
  que um teste daqui acuse; a coordenação é por PR e pelo roteiro de aceite
  integrado.

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| `ddl-auto=update` | Schema derivado do modelo, sem histórico, com risco de alterações destrutivas e sem reprodutibilidade |
| Migrations no repositório do banco (#3) | Separaria o schema do código que o usa; cada mudança exigiria dois PRs coordenados e o RDS deixaria de ser "só infraestrutura" |
| Liquibase | Equivalente; Flyway tem integração mais simples com Spring Boot e SQL puro, que o grupo já domina |
| H2 nos testes | Dialeto diferente do PostgreSQL; migrations e `validate` não seriam de fato exercitados |
