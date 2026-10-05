# Análise e implementação da Sprint 2

O anexo foi tratado como especificação acadêmica. Os exemplos Node.js foram adaptados à aplicação Java/Spring existente; instruções de entrega, criação de conta CloudAMQP e commits não foram executadas como comandos do usuário.

## Requisitos e decisões

| Requisito do roteiro | Implementação |
| --- | --- |
| Mesma linguagem e projeto | Java 21, Spring Boot, mesmo backend e frontend React |
| Exchange topic durável | `iceibank.eventos` em `MessagingConfig` |
| Três filas e routing keys | `fila-agencia-0..2`, `agencia.<id>.creditar`; todas declaradas por qualquer agência para permitir primeiro envio com destino desligado |
| Mensagens persistentes | JSON, delivery mode PERSISTENT, UUID como messageId, publisher confirms e mandatory returns |
| Relógio vetorial | `VectorClockService`: evento local, envio e máximo componente a componente seguido de incremento na recepção; snapshots copiados |
| Substituir REST entre agências | `MessagingService` publica; `CreditConsumer` recebe; endpoint interno antigo retorna 410 depois da verificação da chave |
| Crédito assíncrono | Origem retorna `PUBLISHED`; destino registra `COMPLETED` apenas após crédito; UUID evita reaplicação de mensagem já processada |
| Observabilidade | Vetor nos eventos, transações, JSONL, API, painel e histórico; logs de console com vetor |
| Comparação causal | `mesclar-logs.js` identifica ANTES, DEPOIS, IGUAIS e CONCORRENTES entre agências distintas |
| Continuidade | Particionamento modulo 3 e JWT preservados; operações locais continuam transacionais |
| Funcionalidade adicional | Exchange `iceibank.rejeitados` e uma DLQ por agência; falhas permanentes são rejeitadas, falhas transitórias recebem até três tentativas |
| Respostas e evidências | `RESPOSTAS.md`, `evidencias/sprint2/`, script integrado de validação |

O campo escalar `lamportTimestamp` permanece por compatibilidade com schema, APIs e registros da Sprint 1. Nos registros novos ele é a soma das componentes, usada apenas para ordenação de exibição. A fonte de causalidade é exclusivamente `timestampVetorial`. O serviço antigo de Lamport permanece para consulta e seus testes; não participa do fluxo bancário ativo. Eventos antigos sem vetor são excluídos da comparação causal.

## Persistência e limites

O roteiro usa contas em memória, mas este projeto já utiliza H2 em disco. Preservamos essa melhoria. Para testar resiliência, o script cria uma conta nova, derruba o processo, envia o crédito e reinicia usando o mesmo banco. Para o caso de conta ausente, envia também um crédito para uma conta que nunca existiu: a mensagem termina na DLQ, sem crédito nem transação concluída no destino.

O teste de encerramento abrupto revelou que o WRITE_DELAY padrão do H2 podia perder uma criação recente. As URLs agora usam `WRITE_DELAY=0`, inclusive no teste isolado. Isso não substitui uma política de backup ou um banco adequado à produção.

A mensageria não cria uma transação atômica entre débito, publicação e crédito. Se o broker falhar ou a confirmação se perder após débito, a origem registra INCONSISTENT e entrega incerta. Não há estorno automático: um timeout não prova ausência de entrega. Outbox, reconciliação e Saga/2PC permanecem trabalho posterior. PUBLISHED não evolui automaticamente na origem para COMPLETED; confirmação de crédito não foi escolhida como funcionalidade extra.

O consumidor confirma a mensagem depois que o método transacional de crédito retorna. Em caso de reinício após commit e antes do ACK, o UUID persistido impede crédito duplicado. O log JSONL não é uma transação atômica com o banco e pode conter registros de uma transação posteriormente revertida; o banco e os saldos são a referência para sucesso da operação.

## Executar

Na raiz do repositório, configure um broker próprio:

```powershell
$env:RABBITMQ_URL = 'amqps://usuario:senha@host/vhost'
.\codigo\mvnw.cmd -f codigo/pom.xml package
.\codigo\scripts\start-complete-project.ps1
```

Para RabbitMQ local, o vhost `/` deve ser codificado como `%2F`:

```powershell
docker run -d --name iceibank-rabbitmq -p 127.0.0.1:5672:5672 -p 127.0.0.1:15672:15672 rabbitmq:4-management
$env:RABBITMQ_URL = 'amqp://guest:guest@localhost:5672/%2F'
```

As variáveis precisam ser definidas no terminal que inicia os processos. Não registre a URL com credenciais no Git. Não reutilize filas antigas com argumentos diferentes de DLQ; use um vhost de teste novo ou migre a topologia sem perder mensagens existentes.

## Validar

```powershell
.\codigo\mvnw.cmd -f codigo/pom.xml test
node --test .\codigo\scripts\vector-order.test.mjs
npm --prefix .\codigo\frontend install
npm --prefix .\codigo\frontend run build
.\codigo\scripts\validar-sprint2.ps1 -ManagementUrl 'http://localhost:15672'
```

O teste integrado usa portas 18080..18082 e bancos próprios em `codigo/target/sprint2-<data>`, sem alterar as contas normais. Requer um broker/vhost dedicado sem mensagens pendentes de outras execuções. A consulta de DLQ usa a API de administração do broker local; sem ManagementUrl os demais cenários continuam executáveis. Usuário/senha são parâmetros para ambientes locais diferentes; a consulta atual utiliza o vhost `/`.

Os prints acadêmicos devem mostrar a execução real e Get-Date. Logs em texto são evidência complementar, não substituem automaticamente os prints exigidos. Os commits incrementais ao longo de semanas dependem do desenvolvimento e revisão do aluno: nenhum histórico retroativo foi fabricado.

## Referências técnicas consultadas

- [Spring AMQP: publicação e confirmação](https://docs.spring.io/spring-amqp/reference/amqp/template.html).
- [RabbitMQ: rejeição e dead-letter exchanges](https://www.rabbitmq.com/docs/dlx).
