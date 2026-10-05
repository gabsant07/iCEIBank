# ICEIBank

Projeto de Sistemas Distribuídos — Sprints 1 e 2. API REST, RabbitMQ, relógio vetorial, frontend React e autenticação JWT.

## Tecnologias

- Java 21
- Spring Boot 3.5
- Maven
- Spring Web, Data JPA, Security e AMQP
- H2
- JWT
- React 19 e Vite 8 no frontend separado

A análise e as instruções da Sprint 2 estão em [docs/SPRINT2.md](docs/SPRINT2.md). A documentação histórica da Sprint 1 está em `docs/EXECUCAO.md` e `docs/CHECKLIST-SPRINT1.md`. O frontend separado está em `codigo/frontend/`.

## Arquitetura

Não existe servidor bancário central. O mesmo JAR roda nas portas 8080, 8081 e 8082, com um H2 próprio em cada processo. A agência dona da conta é calculada com `floorMod(accountNumber, 3)`. Transferências remotas são publicadas na exchange topic iceibank.eventos e consumidas de forma assíncrona pelo destino. Configure RABBITMQ_URL antes de iniciar.

## Principais rotas

| Método | Rota | Operação |
| --- | --- | --- |
| POST | `/api/auth/login` | Gerar JWT |
| POST | `/api/accounts` | Criar conta local |
| GET | `/api/accounts/{number}` | Consultar saldo |
| POST | `/api/accounts/{number}/deposits` | Depositar |
| POST | `/api/accounts/{number}/withdrawals` | Sacar |
| GET | `/api/accounts/{number}/history` | Histórico adicional |
| POST | `/api/transfers` | Transferência local ou remota |
| GET | `/api/timeline` | Linha do tempo causal com vetores |

Todas essas rotas, exceto o login, exigem JWT. O console H2 continua público. O endpoint interno antigo retorna 410 depois da validação da chave. Créditos remotos passam pelo RabbitMQ e são idempotentes por UUID. A origem retorna PUBLISHED após confirmação do broker. Falhas de publicação após débito são registradas como INCONSISTENT; não há estorno automático. Veja docs/SPRINT2.md para análise, execução, limites e validação.

## Executar o projeto completo

Com Java 21, Maven e Node.js 20.19 ou superior instalados, execute no PowerShell:

```powershell
.\codigo\scripts\start-complete-project.ps1
```

O script gera o backend, abre as três agências e inicia o frontend em `http://localhost:5173`.

O projeto cria automaticamente duas contas por agência para testes. Consulte a tabela de contas e logins em `docs/EXECUCAO.md`.

Para validar automaticamente os itens técnicos e gerar as evidências reais em `evidencias/sprint1`, execute `scripts/validar-sprint1.ps1` depois de iniciar as agências.
