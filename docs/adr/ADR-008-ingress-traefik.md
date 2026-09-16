# ADR-008: O `Ingress` para o Traefik é declarado por este repositório

- **Status:** Aceito
- **Data:** 2026-09-15
- **Repositório:** fiap-soat-mecanica-api
- **Implementação:** `k8s/gateway/ingress.yaml` (PR #28)

## Contexto

O repositório #2 instala o Traefik via Helm como API Gateway do cluster
(`Service` `NodePort` 30090, entrypoint `web`) e, na sua ADR-003, delega ao
repositório da aplicação a declaração de **para onde** rotear. A API precisava
ficar acessível pelo gateway sem que o repo #2 conhecesse nomes de `Service`
ou portas da aplicação, e sem criar load balancer (custo e IAM no Learner
Lab).

## Decisão

- Este repositório versiona `k8s/gateway/ingress.yaml`: `Ingress`
  `mecanica-gateway`, `ingressClassName: traefik`, anotação
  `router.entrypoints: web`, regra `path: /` (`Prefix`) para o `Service`
  `mecanica-api` na porta 8080, no namespace da API.
- O job `deploy-eks` aplica o `Ingress` depois do `Deployment` e valida a
  rota fazendo `port-forward` no `Service` `traefik` (namespace vindo da
  *Variable* `TRAEFIK_NAMESPACE`, default `traefik`) e chamando
  `/actuator/health/readiness` através dele.
- O `Ingress` **não** é aplicado no Kind: lá não há Traefik e a API é
  alcançada pelo `NodePort` mapeado para `localhost:8080`. O Terraform do
  Kind lê apenas `k8s/*.yaml` (sem subpastas), o que mantém `gateway/` fora
  daquela etapa por construção.
- Middlewares de autenticação no gateway não foram adotados: a autorização
  por JWT continua na própria API (ADR-003), e o gateway apenas roteia.

## Consequências

**Positivas**
- Divisão de responsabilidade clara: o repo #2 entrega o gateway, este
  repositório entrega as rotas; mudar um caminho da API não exige PR no #2.
- O `Ingress` é o objeto padrão do Kubernetes; trocar o Traefik por outro
  controller seria trocar a classe e a anotação.
- A demonstração pedida pelo enunciado ("requisições passam pelo gateway") é
  verificada pela pipeline, não só à mão.

**Negativas / riscos**
- Toda a API, inclusive Swagger e Actuator, fica exposta em `/` pelo gateway;
  não há política de rota no Traefik. Aceito porque a autorização é da API e
  o endpoint do gateway não é público (`NodePort` sem SG aberto).
- Sem TLS: o entrypoint `web` é HTTP. Herdado da decisão do repo #2.
- Um IP de node instável obriga a demonstração a usar `port-forward`.

## Alternativas consideradas

| Alternativa | Motivo da rejeição |
| --- | --- |
| Declarar o `Ingress` no repo #2 | O #2 passaria a conhecer nomes e portas da aplicação; toda mudança de rota exigiria dois PRs |
| `IngressRoute` (CRD do Traefik) | Mais recursos (middlewares nativos), mas amarra os manifests ao Traefik; o `Ingress` padrão basta |
| `Service` tipo `LoadBalancer` direto na API | Cria NLB/CLB pago e contorna o requisito de gateway |
| Autenticação JWT como middleware do Traefik | Duplicaria a validação que a API já faz com dois emissores; o token de cliente exige claims que o middleware padrão não confere |
