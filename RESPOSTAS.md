# RESPOSTAS - Sprint 1: ICEIBank

**Alunos:** Gabriel Santiago
**Linguagem escolhida:** Java 21 com Spring Boot 3.5

---

## Nota de transparência - uso de IA

O projeto teve apoio de inteligência artificial em algumas etapas de implementação, principalmente na revisão da configuração do Spring, na criação dos scripts de execução e testes e em partes da integração do frontend com as APIs. No frontend, a ferramenta também ajudou na customização visual e na organização das chamadas ao backend.

Depois desse apoio, o código foi revisado e testado pelo grupo. As regras de negócio, a divisão das agências, as transferências, o relógio de Lamport e a autenticação foram conferidos diretamente na aplicação, de modo que os integrantes conseguem explicar as decisões utilizadas.

---

## Funcionalidade adicional - seção 2.1

**Funcionalidade escolhida:** histórico de transações por conta.

Foi criado o endpoint protegido `GET /api/accounts/{accountNumber}/history`. O backend verifica se a conta pertence à agência que recebeu a requisição, confirma que ela existe e consulta no H2 todas as transações em que aparece como origem ou destino. O resultado é devolvido do registro mais recente para o mais antigo.

O histórico apresenta criação de conta, depósito, saque, transferência local, transferência enviada, crédito remoto recebido e transferência inconsistente. Cada registro informa tipo, valor, origem, destino, status, agência, data e timestamp de Lamport.

A funcionalidade está principalmente no `AccountController`, no `AccountService` e no `BankTransactionRepository`. Ela foi escolhida porque aproveita os registros que o sistema já salva e oferece uma consulta parecida com um extrato bancário. Também facilita a conferência das transferências e da falha conhecida.

Ela pode ser testada pelo frontend ou diretamente pela API. O script `scripts/validar-sprint1.ps1` também consulta esse endpoint durante a validação.

---

## Parte B - Relógio de Lamport - seção 6.4

### 1. Por que utilizar `max(contadorLocal, timestampRecebido) + 1`?

O relógio não pode voltar para um número menor quando uma mensagem chega. Se a agência estiver no contador 10 e receber uma mensagem com timestamp 3, adotar apenas o valor recebido faria os próximos eventos parecerem anteriores a eventos que já aconteceram.

Por isso, o método `receiveEvent()` do `LamportClockService` escolhe o maior valor entre o contador atual e o recebido. Depois soma 1 para registrar que o recebimento aconteceu após o envio. O `max` impede o retrocesso e o `+ 1` deixa o recebimento logicamente posterior aos eventos anteriores das duas agências.

### 2. Agência no contador 10 recebendo timestamp 3

O cálculo é `max(10, 3) + 1 = 11`. Portanto, o contador passa para 11.

Cada agência avança conforme processa seus próprios eventos. Uma instância com mais operações normalmente terá um contador maior. Se uma agência atrasada envia para outra que já está adiantada, a destinatária apenas aumenta uma unidade. No caso contrário, a agência que está atrás pode dar um salto.

O timestamp de Lamport não representa horário real nem quantidade de trabalho. Ele serve para manter a ordem lógica, principalmente quando existe uma relação de causa e efeito entre eventos distribuídos.

---

## Parte D - Transferências - seção 8.3

### 1. Diferença entre transferência local e transferência entre agências

O projeto calcula a agência proprietária com `Math.floorMod(accountNumber, 3)`. Quando origem e destino pertencem à mesma agência, o `TransferService` realiza uma transferência local. Débito e crédito acontecem em uma única transação do H2 daquela instância. A operação recebe um timestamp por `localEvent()`, pois tudo ocorre no mesmo processo e utiliza o mesmo relógio.

Quando o destino pertence a outra agência, a operação envolve dois processos e dois bancos independentes. A origem confirma o débito no próprio banco e utiliza `sendEvent()` para gerar o timestamp enviado na requisição REST. Ao receber o crédito remoto, o destino chama `receiveEvent(timestampRecebido)`. Assim, o crédito fica logicamente depois do envio que o provocou.

### 2. O que acontece na falha conhecida?

Na transferência remota, o débito da origem é confirmado antes da resposta do destino. Se a outra agência estiver desligada, a chamada REST falha e o backend responde com HTTP 502.

O valor debitado não é estornado automaticamente. A transação é salva com status `INCONSISTENT` e tipo `REMOTE_TRANSFER_FAILED`. O evento também fica registrado no arquivo JSONL da agência.

Isso mostra que a transferência remota ainda não possui atomicidade entre os dois bancos: uma parte foi concluída na origem, enquanto a outra não aconteceu no destino. O sistema deixa a falha visível, mas ainda não corrige o saldo sozinho.

O problema pode ser reproduzido pelo `scripts/validar-sprint1.ps1`. O script interrompe a Agência 2, tenta enviar uma transferência para ela, confirma o retorno 502 e consulta o histórico da origem.

### 3. Como corrigir esse problema futuramente?

Uma opção seria o commit em duas fases, ou 2PC. Primeiro, as agências preparariam a operação. A origem reservaria o valor e o destino confirmaria que consegue receber. Débito e crédito somente seriam confirmados quando os dois lados estivessem prontos. Se uma parte falhasse, a operação seria cancelada.

Outra opção seria uma Saga. Cada etapa teria uma ação de compensação. Se o débito fosse concluído e o crédito falhasse, a compensação devolveria o dinheiro para a origem.

Também seria possível manter a transferência pendente e repetir o crédito. O endpoint remoto já evita processar duas vezes o mesmo identificador, o que ajuda em uma estratégia com novas tentativas.

---

## Parte E - Linha do tempo unificada - seção 10.3

### 1. Timestamps diferentes provam causalidade?

Não. Se A realmente aconteceu antes de B e influenciou B, o relógio de Lamport garante que o timestamp de A será menor. Porém, dois números diferentes não são suficientes para concluir que um evento causou o outro.

Duas agências podem executar operações independentes e ainda gerar números diferentes. Esses eventos são concorrentes, mesmo aparecendo em determinada ordem. O relógio mantém a causa antes do efeito, mas a ordem numérica sozinha não mostra se existe ligação entre todos os eventos.

### 2. Por que o Lamport não identifica todos os eventos concorrentes?

Cada processo mantém apenas um contador. Quando os valores são diferentes, não dá para saber somente pelos números se um evento influenciou o outro ou se os dois aconteceram de forma independente.

O relógio vetorial mantém um contador para cada processo. Comparando os vetores, é possível identificar se um evento veio antes, depois ou se os dois são concorrentes. Essa é uma informação que o Lamport sozinho perde.

### Observação sobre a linha do tempo

Cada agência grava seus eventos nos arquivos `data/eventos-agencia-0.jsonl`, `data/eventos-agencia-1.jsonl` e `data/eventos-agencia-2.jsonl`.

O `scripts/mesclar-logs.js` lê os três arquivos e organiza os registros pelo timestamp de Lamport. Quando necessário, utiliza agência e horário físico como desempate visual. O endpoint `GET /api/timeline` faz uma consulta semelhante para mostrar a linha do tempo no frontend.

Eventos locais de agências diferentes podem ter timestamps iguais ou próximos sem depender um do outro. Em uma transferência remota, o envio da origem precisa aparecer logicamente antes do crédito recebido no destino.

---

## Parte F - Autenticação JWT - seção 11.3

### Justificativas de design

O login utiliza usuário e senha. Os usuários são armazenados no H2 pela entidade `AppUser`, e as senhas são protegidas com BCrypt. O projeto cria o usuário `admin` e um usuário de teste para cada agência.

Depois do login, o `AuthService` gera um JWT com nome do usuário, identificador da agência, emissão e expiração. O `JwtAuthenticationFilter` valida o token antes de liberar as rotas bancárias. O login e o console H2 são públicos; contas, transferências e timeline exigem autenticação.

Um token emitido por uma agência não é aceito nas outras, pois o backend compara o `agencyId` do token com o identificador da instância atual.

A comunicação interna usa outro mecanismo. O endpoint `/api/transfers/internal/credit` exige `X-Agency-Key`, configurada por `INTERNAL_API_KEY`. A origem envia essa credencial automaticamente e o destino a valida. O token do usuário não é repassado nessa chamada.

### 1. Diferença entre autenticação e autorização

Autenticação confirma quem está acessando o sistema. No projeto, acontece pelo login e pela validação do JWT.

Autorização decide quais ações a identidade pode executar. A aplicação possui controle no nível da agência, recusando operações de contas que não pertencem à instância atual. Também separa requisições de usuário das chamadas internas.

Ainda não existe autorização por titular. Um usuário autenticado consegue movimentar qualquer conta local, pois o backend não compara o usuário do token com o titular. Para impedir isso, seria necessário associar a conta ao usuário e verificar essa relação em cada operação.

### 2. Por que o servidor não precisa manter uma sessão em memória?

O JWT é assinado. O backend verifica a assinatura, a agência e a expiração usando as informações do token. Não existe uma sessão guardada no servidor, pois o Spring Security está configurado como `STATELESS`.

No projeto, o `DatabaseUserDetailsService` ainda consulta o usuário para confirmar que ele existe. Mesmo assim, qualquer instância consegue validar o JWT localmente, sem depender de uma sessão compartilhada.

A desvantagem é que um token válido continua utilizável até expirar, a menos que seja criado algum mecanismo adicional de revogação.

### 3. O que acontece se a chave secreta vazar?

Quem descobrir a chave poderá criar tokens com assinatura válida, escolher usuários e alterar dados como agência e expiração. O servidor poderá aceitar esses tokens como verdadeiros.

Nesse caso, será necessário trocar o segredo imediatamente, invalidando também os tokens legítimos emitidos com a chave anterior. Em produção, a chave deve ficar fora do código, ser diferente em cada ambiente e possuir um processo de rotação.

---

## Parte G - Frontend - seção 12.3

### Justificativas de design

O frontend foi desenvolvido em JavaScript com React e Vite. Existe uma aplicação web, mas o usuário escolhe no login qual agência acessar. A escolha define se as chamadas seguem para `localhost:8080`, `localhost:8081` ou `localhost:8082`.

Depois do login, a sessão é guardada no `localStorage` pela chave `iceibank.session`. Ela contém token, URL e identificação da agência. Isso mantém o login depois de atualizar a página.

O risco do `localStorage` é um código malicioso da página conseguir ler o token. Em uma aplicação real, seriam necessárias proteções fortes contra XSS e poderia ser estudado o uso de cookies HttpOnly.

### 1. Como o frontend reenvia o token?

O login é controlado pelo `AuthContext`. Quando as credenciais são aceitas, o token retornado pela API é colocado na sessão e salvo no navegador.

Todas as chamadas passam pela função `apiRequest()`, em `frontend/src/services/api.js`. Antes do `fetch`, ela verifica o token e adiciona `Authorization: Bearer <token>`. Assim, as páginas não precisam repetir essa lógica.

### 2. O que acontece se o token expirar?

Quando a API responde com erro, o serviço cria um `ApiError`. A página captura esse erro e usa o componente de notificação para mostrar a mensagem na tela.

Na versão atual, a interface avisa o problema de autenticação, mas não remove automaticamente a sessão nem redireciona imediatamente para o login. Uma melhoria futura seria tratar especificamente o HTTP 401, limpar o `localStorage` e realizar o logout automático.

### 3. Onde ficam Model, View e Controller?

O acesso aos dados fica principalmente em `frontend/src/services/api.js`, que representa parte do Model. O `AuthContext` mantém o estado da autenticação e da agência escolhida.

A View é formada pelos arquivos de `frontend/src/pages` e `frontend/src/components`, onde ficam formulários, tabelas, menu, modal e mensagens.

O papel de Controller é dividido entre `App.jsx`, `AuthContext` e as páginas. Eles recebem as ações da interface, chamam os serviços e atualizam o estado. Em um React pequeno, essa divisão é comum, embora não seja um MVC clássico.

No backend, a separação é mais direta: controllers recebem as requisições REST, services guardam as regras de negócio, repositories acessam o H2 e entities representam os dados persistidos.

---

## Sprint 2 — Parte B, seção 6.4

1. Dez agências exigiriam dez componentes em cada vetor, em vez de três. Espaço na mensagem e custo de atualização/comparação crescem O(p), com p processos. Dez é pequeno, mas milhares de participantes tornam o custo relevante e exigem estudar representação esparsa, escopo de participantes ou outros mecanismos. A implementação atual valida três componentes e precisaria ser parametrizada para essa evolução.
2. [3,1,0] aconteceu antes de [3,2,0]: 3<=3, 1<2, 0<=0. Há desigualdade estrita em pelo menos uma posição.
3. [3,1,0] e [1,3,0] são concorrentes: o primeiro tem componente 0 maior, o segundo tem componente 1 maior. Nenhum vetor domina o outro.

## Sprint 2 — Parte C, seção 7.5

1. No teste integrado de 29/09/2026, uma conta 100 foi criada na Agência 1 com saldo 100. Após derrubar o processo, a origem publicou 30 com status PUBLISHED. Ao reiniciar com o mesmo H2, a Agência 1 consumiu a mensagem e o saldo passou a 130. O log registrou REMOTE_TRANSFER_RECEIVED com vetor que incorpora o envio da origem e incrementa a componente do destino. Aqui a conta sobrevive porque já existe persistência em disco, diferente do exemplo em memória do roteiro. Um segundo teste enviou 5 para a conta 1000, inexistente: o broker entregou a mensagem, o consumidor registrou CREDIT_REJECTED/Account not found e a mensagem foi para fila-agencia-1.dlq. Portanto, entrega não implica aplicação do crédito. O teste revelou também a necessidade de WRITE_DELAY=0 no H2 para preservar commits recentes após encerramento abrupto.
2. Na Sprint 1 o destino precisava estar disponível durante a chamada REST. Agora a origem pode publicar enquanto o destino está desligado, e a fila retém o crédito. Ainda não há atomicidade entre banco de origem, broker e banco de destino. Uma falha de publicação após débito, uma confirmação perdida ou uma conta ausente exigem reconciliação; DLQ não estorna dinheiro nem implementa Saga. O status PUBLISHED confirma o broker, não o crédito.
3. O consumidor não precisa do JWT de um usuário porque é um canal interno de mensagens. Porém isso não torna o canal automaticamente seguro: qualquer cliente com credenciais e permissão de publicar pode injetar créditos. O código valida partição, origem, valor, vetor e UUID, mas não prova criptograficamente qual agência produziu o evento. Em desenvolvimento há credenciais locais guest; em produção seriam necessários usuários e permissões restritos por agência/vhost, TLS (amqps), segredos externos e, conforme o modelo de ameaça, assinatura das mensagens. Não basta confiar em sourceAgency recebido no JSON.

## Sprint 2 — Parte D, seção 8.3

1. O vetor conserva a informação de progresso conhecida de cada processo. A atualização por máximo na recepção propaga as dependências. Comparar todas as componentes permite provar a ordem causal ou a incomparabilidade; apenas comparar dois números de Lamport não permite essa conclusão.
2. Nos logs reais, E0 é DEPOSIT na Agência 0 com [1,0,0], e E2 é DEPOSIT na Agência 2 com [0,0,1]. O script classificou E0/E2 como CONCORRENTES. São depósitos independentes sem mensagens entre as agências. Concorrência causal não exige simultaneidade de relógio físico. Já o envio REMOTE_TRANSFER_SENT precede o crédito REMOTE_TRANSFER_RECEIVED correspondente: o vetor do envio é menor ou igual componente a componente, e o script imprime ANTES para esse par. Consulte evidencias/sprint2/linha-do-tempo-causal.txt.
3. Comparar todos os pares custa O(n²*p), considerando também p componentes. Milhões de eventos tornam o custo e a própria saída enormes. Pode-se limitar janelas temporais e pares relevantes, indexar por agência/transação, manter um grafo de dependências, processar incrementos em streaming ou selecionar apenas eventos necessários para uma investigação. Uma ordenação visual simples não substitui a comparação causal.

## Sprint 2 — Funcionalidade adicional: dead-letter queues

MessagingConfig configura iceibank.rejeitados e fila-agencia-<id>.dlq. Créditos inválidos, por exemplo para conta inexistente, são rejeitados com AmqpRejectAndDontRequeueException. A configuração de retry limita a três tentativas; erros transitórios que esgotam tentativas também são rejeitados. O ACK de um crédito válido só acontece após o retorno do serviço transacional. O teste integrado consultou a API real do RabbitMQ e encontrou mensagens prontas em fila-agencia-1.dlq. Não há reprocessamento automático nem estorno: a DLQ preserva o payload para investigação.

## Sprint 2 — Validação e uso de IA

Foram executados testes Java (JWT, contas, partição, transferência local, relógio vetorial, publicação com mensageria simulada, crédito idempotente e falhas), testes Node da comparação causal, build React e validação com três processos Java e RabbitMQ real. Transcript e logs estão em evidencias/sprint2/. Login e depósito também foram executados pelo React no navegador, com saldo final de 1510 e captura frontend-regressao.png. Os prints acadêmicos com Get-Date em terminal e o histórico incremental de commits continuam itens próprios de entrega; não foram fabricados.

Foi utilizado Codex/OpenAI para analisar o roteiro, adaptar e implementar código, escrever testes e documentação e executar validações. Esta declaração identifica o apoio utilizado; o aluno deve revisar, compreender e ser capaz de explicar cada trecho antes da entrega.
