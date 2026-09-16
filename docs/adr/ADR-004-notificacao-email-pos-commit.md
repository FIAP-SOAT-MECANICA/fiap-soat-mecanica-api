# ADR-004: Notificação por e-mail via porta de saída, após o commit, sem retry

- **Status:** Aceito
- **Data:** 2026-09-15 (decisão tomada na Fase 2, PR #20)
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `NotificacaoOrdemServicoPort`, `NotificarAlteracaoSituacaoOrdemServicoUseCase`, `adapters/out/notification`

## Contexto

O fluxo da ordem de serviço exige que o cliente seja avisado quando o
orçamento fica pronto (`AGUARDANDO_APROVACAO`) e a cada mudança de situação.
O envio não pode reverter a transição já persistida, não pode expor
credenciais SMTP e precisa ser testável sem servidor de e-mail. Na Fase 3 a
API roda no EKS sem provedor SMTP disponível no Learner Lab.

## Decisão

- A camada de aplicação declara a porta `NotificacaoOrdemServicoPort`; o
  adapter `adapters/out/notification` a implementa com Spring Mail
  (`JavaMailSender`). O domínio não conhece SMTP.
- Os casos de uso de transição continuam donos das regras; depois de
  persistir, chamam `NotificarAlteracaoSituacaoOrdemServicoUseCase`, que
  resolve o destinatário pelo caminho OS → veículo → cliente e **registra o
  envio como `TransactionSynchronization.afterCommit`**. Se não houver
  transação ativa, executa imediatamente.
- Falha ao resolver o destinatário ou ao enviar é registrada em log
  (`order_service_notification_*_failed`, com ID da OS e tipo da exceção) e
  **não** altera a resposta HTTP nem desfaz a transição.
- Não há retry, fila, envio assíncrono nem *Outbox Pattern* nesta versão.
- `app.notification.email.enabled` (`EMAIL_NOTIFICATIONS_ENABLED`) liga e
  desliga o recurso: `true` no Compose (com MailHog), `false` no perfil de
  teste e `false` no `ConfigMap` do EKS. Toda configuração SMTP é
  externalizada por variável de ambiente; nenhuma credencial é versionada.

## Consequências

**Positivas**
- A transição da OS nunca fica refém do SMTP: um servidor fora do ar não gera
  erro para o mecânico.
- O e-mail só sai depois que o estado foi realmente gravado, evitando avisar
  o cliente de uma transição que sofreu rollback.
- Testes de caso de uso usam a porta com *mock*; o adapter é testado com
  `JavaMailSender` mockado, então a suíte não abre conexão.

**Negativas / riscos**
- Uma falha de envio perde a notificação; não há reprocessamento. Aceito
  para o escopo, com o log estruturado como evidência.
- No EKS o recurso fica desligado, então a demonstração do e-mail é local
  (MailHog em `http://localhost:8025`).
- O envio ocorre na thread da requisição após o commit; um SMTP lento
  aumenta a latência da resposta (timeouts de 5 s limitam o impacto).

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| Enviar dentro da transação | Falha de SMTP reverteria a transição, ou o e-mail sairia antes do commit |
| Outbox + job assíncrono | Mais robusto, porém adiciona tabela, scheduler e testes; desproporcional ao escopo atual |
| Amazon SES no EKS | Learner Lab sem IAM Role para o pod; verificação de domínio/remetente fora do escopo |
| Evento de domínio + listener Spring (`@TransactionalEventListener`) | Equivalente em efeito; a porta explícita deixa a dependência visível no caso de uso e mantém o domínio sem Spring |
