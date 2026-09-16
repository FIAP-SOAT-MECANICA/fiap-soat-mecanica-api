# ADR-001: Clean Architecture com portas e adaptadores

- **Status:** Aceito
- **Data:** 2026-09-15 (decisão tomada na Fase 1, registrada aqui)
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `src/main/java/br/com/fiap/soat/mecanica/{domain,application,adapters,config}`

## Contexto

O Tech Challenge pede uma API de oficina mecânica com regras de negócio
não triviais: ciclo de vida da ordem de serviço com seis situações,
orçamento aprovado pelo cliente, baixa de estoque de peças e três perfis de
acesso. Ao longo das fases a aplicação ganhou notificação por e-mail,
segundo emissor de JWT, logs estruturados e instrumentação — tudo isso sem
que o domínio pudesse virar refém de Spring, JPA ou SMTP. O grupo também
precisava testar as regras sem subir banco ou contexto Spring.

## Decisão

Organizar o código em quatro camadas com dependência sempre apontando para o
domínio:

| Pacote | Responsabilidade | O que **não** pode conter |
| --- | --- | --- |
| `domain` | Entidades (`Cliente`, `Veiculo`, `OrdemServico`, `PrestacaoServico`, `AlocacaoPeca`, `Peca`, `Servico`, `Usuario`), enums, exceções de negócio, *value objects* (`CPF`, `CNPJ`, `Email`, `Placa`, `Senha`, `Telefone`) e **portas de saída** (`*Repository`) | Anotações de Spring, JPA, Jackson ou Lombok de framework |
| `application` | Um caso de uso por classe (`*UseCase`), DTOs de aplicação e portas de saída não-persistentes (`NotificacaoOrdemServicoPort`, `CurrentUserPort`, `CurrentClientePort`) | Regra de negócio (fica no domínio) e detalhes de HTTP/SQL |
| `adapters/in/web` | Controllers, DTOs de requisição/resposta, mappers e o filtro JWT | Regra de negócio |
| `adapters/out` | `persistence` (entidades JPA, `JpaRepository`, mappers domínio↔JPA), `notification` (adapter SMTP), `security` (JWT e hash de senha) | Regra de negócio |
| `config` | Segurança, OpenAPI, tratamento global de exceções e observabilidade | Regra de negócio |

Regras derivadas:

- Entidades JPA e entidades de domínio são classes distintas, ligadas por
  mappers; o domínio nunca é anotado com `@Entity`.
- Validações de formato vivem nos *value objects* e falham na construção, de
  modo que um objeto de domínio inválido não existe.
- Casos de uso recebem portas por construtor, o que permite testá-los com
  *mocks* sem Spring.
- Um caso de uso de aplicação não chama controller nem conhece DTO web.

## Consequências

**Positivas**
- Testes unitários de domínio e casos de uso rodam sem container; só os
  testes de adapter de persistência sobem PostgreSQL (ver ADR-011).
- A troca do adapter SMTP, do emissor de JWT ou do mecanismo de log não tocou
  o domínio (ver ADR-003, ADR-004 e ADR-010).
- A separação facilitou a análise SonarQube por camada e a cobertura mínima
  de 80%.

**Negativas / riscos**
- Duplicação de modelos (domínio, JPA, DTO web) e mappers em cada módulo; é o
  custo assumido para manter o domínio puro.
- Alguns casos de uso de ordem de serviço orquestram vários repositórios e
  cresceram; foram separados por operação (`Cadastrar…`, `Notificar…`,
  `Cancelar…`) para conter isso.
- Anotações de Lombok aparecem no domínio para reduzir boilerplate; é uma
  concessão consciente, sem impacto em runtime.

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| Camadas clássicas controller → service → repository com entidades JPA no domínio | Regras de negócio acabariam acopladas a JPA e a transações; testes exigiriam banco |
| Anemic domain + serviços transacionais | Invariantes da OS (transições, totais, estoque) ficariam espalhadas em serviços |
| Módulos Maven separados por camada | Mais rígido, porém mais infraestrutura de build para um único serviço; a separação por pacote atende o escopo |
