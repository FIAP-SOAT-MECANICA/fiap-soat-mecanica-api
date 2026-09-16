# ADR-010: Logs estruturados em JSON com `requestId` e eventos de negócio nomeados

- **Status:** Aceito
- **Data:** 2026-09-15
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `config/observability/HttpRequestLoggingFilter`, `BusinessEventLogger`, `config/exception/ErrorResponse`, `application.properties` (PR #31)

## Contexto

O enunciado pede logs estruturados com correlação entre requisições. Antes
do PR #31 os logs eram texto livre do Spring, sem identificador de
requisição, e as respostas de erro não davam ao cliente nada que pudesse ser
cruzado com o log. Os logs também não podiam conter senha, token, CPF ou
e-mail, já que a pipeline falha se um valor de segredo aparecer neles.

## Decisão

- **Formato:** `logging.structured.format.console=logstash` do Spring Boot
  3.5, com o nome do serviço adicionado a cada linha. Sem dependência extra.
- **Correlação HTTP:** `HttpRequestLoggingFilter` (ordem máxima) aceita
  `X-Request-Id` do cliente se casar com `[A-Za-z0-9._-]{1,100}`, senão gera
  um UUID; coloca o valor no MDC como `requestId` e o devolve no header da
  resposta. Ao final da requisição registra o evento
  `http_request_completed` com `method`, `route` (o *pattern* do handler, ou
  a URI com UUIDs e números substituídos por `{id}`), `status` e
  `durationMs`. Chamadas a `/actuator/health/*` não são logadas.
- **Erros:** `ErrorResponse` inclui `errorId`, preenchido com o `requestId`
  do MDC (ou um UUID novo), para que o usuário informe o que procurar no log.
  O `GlobalExceptionHandler` loga violações de validação em `DEBUG` com
  contagens, nunca com o payload.
- **Eventos de negócio:** `BusinessEventLogger` centraliza
  `order_service_created`, `order_service_status_changed` e
  `order_service_cancelled`, sempre com IDs (OS, veículo, mecânico) e
  situações anterior/nova; o caso de uso de notificação emite
  `order_service_notification_sent`, `…_preparation_failed` e
  `…_send_failed` com o tipo da exceção. O log da mudança de situação é
  registrado **após o commit**, junto da notificação (ADR-004).
- **Nunca em log:** senha, token JWT, chave, CPF, e-mail ou corpo da
  requisição. Falhas são descritas por tipo de exceção e IDs.
- Nível `INFO` para o pacote da aplicação em produção; `DEBUG` e `show-sql`
  apenas no perfil `local`.

## Consequências

**Positivas**
- Cada linha é JSON com `requestId`, `service`, `event` e campos tipados;
  o Datadog indexa e filtra sem *grok*. Junto ao agente Java (ADR-009),
  `traceId`/`spanId` também entram no MDC.
- `errorId` na resposta fecha o ciclo suporte → log.
- Métricas simples (latência por rota, taxa de erro) podem ser derivadas dos
  eventos `http_request_completed` mesmo sem APM.

**Negativas / riscos**
- A API aceita `X-Request-Id` mas não o `x-correlation-id` que a Auth (repo
  #1) gera; a correlação ponta a ponta com a Lambda exige que o cliente
  reenvie o valor como `X-Request-Id`. Registrado na RFC-001.
- Rotas sem handler (404) caem na normalização por regex, que cobre UUIDs e
  números, mas não outros identificadores.
- Logs em `INFO` por requisição têm custo de ingestão no Datadog; health
  checks foram excluídos por isso.

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| `logstash-logback-encoder` | Dependência extra para o que o Spring Boot 3.5 já faz nativamente |
| Micrometer Tracing / Sleuth para o ID | O agente OpenTelemetry já injeta `traceId`; o `requestId` cobre o caso sem agente (Compose, testes) |
| Logar payloads de requisição/resposta | Risco direto de vazar senha, CPF e token; proibido pelo requisito e pela verificação da pipeline |
| Log de auditoria em tabela | Fora do escopo; os eventos nomeados no log já dão a trilha da OS |
