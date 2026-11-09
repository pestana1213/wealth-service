# Wealth Service

MVP para acompanhar dinheiro contribuído e onde está alocado, em EUR.
O modelo tem apenas cinco entidades JPA:

- `User`: proprietário das contas, buckets e transações.
- `Account`: local onde o dinheiro está (banco, corretora, etc.).
- `Bucket`: finalidade do dinheiro (casa, emergência, carro, VWCE, etc.).
- `Goal`: montante pretendido e data opcional para um bucket.
- `Transaction`: entrada, saída ou transferência, com montante positivo,
  categoria, data e descrição.

Investimentos são buckets com contribuições categorizadas como `INVESTMENT`.
Não existem cotações, posições ou valorização de mercado.

## Convenções das transações

`providerAccountId` e `sourceBucketId` identificam a origem;
`destinationAccountId` e `destinationBucketId` identificam o destino.
Um bucket nulo representa dinheiro não alocado nessa conta.

| Operação | Tipo | Conta origem → destino | Bucket origem → destino |
| --- | --- | --- | --- |
| Receber salário | INCOME | nenhum → Bank | nenhum → nenhum |
| Alocar 3.000 € a VWCE no Bank | TRANSFER | Bank → Bank | nenhum → VWCE |
| Mover os mesmos 3.000 € para XTB | TRANSFER | Bank → XTB | VWCE → VWCE |
| Libertar uma alocação | TRANSFER | Bank → Bank | VWCE → nenhum |
| Pagar uma despesa da casa | EXPENSE | Bank → nenhum | Casa → nenhum |

Os saldos atuais são persistidos em `Account.currentAmount` e
`Bucket.currentAmount`, como `BigDecimal` com duas casas decimais.
É possível indicar um saldo inicial no construtor; por omissão é zero.
O dashboard poderá ler estes valores diretamente:

- Conta: `Account.currentAmount`, o dinheiro que está nessa conta.
- Bucket: `Bucket.currentAmount`, o dinheiro alocado a essa finalidade.
- Total poupado: soma dos saldos das contas.
- Cash disponível: total poupado menos soma dos saldos dos buckets.

Contas e buckets são duas perspetivas sobre o mesmo dinheiro; não se somam
entre si. Transferências não alteram o total poupado. Uma transferência
entre contas que preserva o bucket também não altera o saldo desse bucket.
Os saldos iniciais podem ser definidos diretamente, sem importar todo o histórico.
Um saldo inicial definido diretamente não deve ser novamente somado como entrada.

## Estado da implementação

Entidades e migração Liquibase inicial. As referências entre entidades são
campos UUID, sem associações JPA; as chaves estrangeiras mantêm a integridade
referencial na base de dados. Existem endpoints de criação de utilizadores,
contas, buckets e objetivos. Os fluxos `INCOME`, `EXPENSE` e `TRANSFER` atualizam as contas e buckets
afetados e gravam a transação no mesmo `TransactionTemplate`.
Mover dinheiro acontece num único sítio, o `Ledger`: é ele que adquire os locks,
aplica a aritmética e grava o histórico, tudo dentro da mesma transação de base
de dados. O `CreateTransactionAction` valida o pedido, chama o `Ledger` e traduz
o resultado em estado HTTP, fora da transação. Uma reversão passa pelo mesmo
`Ledger`, com a ligação à transação original num `NewTransaction`.
As referências de origem e destino devem pertencer ao utilizador da transação.
As contas e buckets são carregados com `FOR UPDATE NOWAIT`, primeiro contas
e depois buckets, ordenados por UUID e sem bloquear o mesmo registo duas vezes.
Se algum estiver bloqueado, a operação faz rollback e devolve `409 Conflict`,
sem esperar pelo lock da linha nem repetir automaticamente. `@Version` permanece
como proteção adicional contra atualizações concorrentes.
`EXPENSE` subtrai da conta e do bucket de origem opcional; `TRANSFER` subtrai
da origem e soma ao destino. Uma conta ou bucket presente nos dois lados
mantém o saldo.

Saldo suficiente é verificado nos buckets e não nas contas. Uma conta pode
ficar negativa: um saldo devedor ou um cartão de crédito é um estado real de
uma conta, e uma despesa pode levá-la abaixo de zero. Um bucket não pode, por
ser uma parte de dinheiro que existe nas contas; um movimento que o levasse
abaixo de zero devolve `400 Bad Request` e não altera nada. A regra está no
`Ledger`, na anotação `@PositiveOrZero` da entidade e na restrição
`buckets_amount_not_negative`; as contas não têm restrição equivalente.

Persistir uma `Transaction` diretamente no repository não atualiza saldos;
usar a action.
A base de dados já valida montantes positivos e a estrutura das transferências.

`POST /transactions` valida o pedido antes de tocar em saldos: `userId`, `type`,
`category`, `occurredAt` e `transactionAmount` são obrigatórios, o montante é
positivo com até 17 algarismos inteiros e duas casas decimais, `occurredAt` não
pode ser futuro e `description` está limitada a 255 caracteres. Dados inválidos
devolvem `400 Bad Request` em `application/problem+json`, com os campos em causa
na propriedade `errors`. As referências de conta e bucket não são anotadas: quais
podem ser nulas depende do `type`, pelo que essa regra continua na action e na
restrição `transaction_accounts`.

O PostgreSQL é configurado em `application.properties`; o Hibernate valida
o esquema criado pelo Liquibase. Java 25 é necessário.

## Visualização

Os endpoints de leitura são:

- `GET /users/{userId}/dashboard`: total, montante alocado, cash disponível,
  contas, buckets e objetivos (incluindo progresso atual de cada objetivo).
- `GET /users/{userId}/accounts`: contas e respetivos saldos.
- `GET /users/{userId}/buckets`: buckets e respetivos saldos.
- `GET /users/{userId}/goals`: objetivos e progresso atual.

Todos devolvem `404 Not Found` quando o utilizador não existe.

## Swagger / OpenAPI

A documentação está desligada por omissão. Com `API_DOCS_ENABLED=true`, a
documentação interativa fica disponível em `/swagger-ui.html` e o documento
OpenAPI em `/api-docs`.

## Métricas e logs

Todas as requests HTTP são registadas pelo `http.server.requests` do Spring, com
método, template da rota (`uri`, por exemplo `/buckets/{bucketId}`), status,
exceção e duração, e com histograma para os percentis.
Os endpoints Actuator `health`, `info` e `metrics` estão expostos em
`/actuator`. Consultar, por exemplo, `/actuator/metrics/http.server.requests`.

## Criar utilizador e conta

`POST /users` — cria um utilizador e devolve `201 Created` com `id`, `name` e `email`.

```json
{"name": "João", "email": "joao@example.com"}
```

`POST /accounts` — cria uma conta e devolve `201 Created` com `id`, `userId`,
`name` e `currentAmount`. Usar o ID devolvido na criação do utilizador:

```json
{"userId": "<UUID do utilizador>", "name": "Bank", "currentAmount": 55000.00}
```

`currentAmount` é opcional (omitido ou nulo assume `0.00`), com até 17
algarismos inteiros e duas casas decimais. O saldo é definido diretamente;
não é criada uma transação inicial. Saldos negativos são aceites para contas
com saldo devedor. Os nomes são obrigatórios, limitados a 255 caracteres,
e guardados sem espaços nas extremidades.

Dados inválidos devolvem `400 Bad Request`; um `userId` que não existe devolve
`404 Not Found`. Estas operações criam registos de domínio; autenticação e
login ainda não estão implementados.

O email é obrigatório nos novos utilizadores, validado até 254 caracteres,
guardado em minúsculas e sem espaços nas extremidades. Emails repetidos,
independentemente de maiúsculas/minúsculas, devolvem `409 Conflict`.
A migração preserva utilizadores antigos com email nulo; os endereços reais
terão de ser preenchidos posteriormente. O índice único aplica-se aos emails
preenchidos, incluindo registos concorrentes.

## Criar bucket e objetivo

`POST /buckets` — devolve `201 Created` com `id`, `userId`, `name` e `currentAmount`.

```json
{"userId": "<UUID do utilizador>", "name": "Casa", "currentAmount": 27000.00}
```

O utilizador deve existir. O nome é obrigatório, até 255 caracteres, e é
guardado sem espaços nas extremidades. O saldo é opcional (omitido ou nulo
assume zero), não negativo e limitado a 17 algarismos inteiros e duas casas
decimais. Este saldo inicial representa uma alocação já existente; criar o
bucket não altera saldos de contas nem cria uma transação.

`POST /goals` — devolve `201 Created` com `id`, `bucketId`, `goalAmount` e `goalObjective`.

```json
{"bucketId": "<UUID do bucket>", "goalAmount": 50000.00, "goalObjective": "2027-12-31"}
```

O bucket deve existir. O montante é obrigatório, positivo e limitado a 17
algarismos inteiros e duas casas decimais. A data é opcional, em formato
`YYYY-MM-DD`; criar um objetivo não altera o saldo do bucket.

Ambos devolvem `400 Bad Request` para dados inválidos e `404 Not Found`
quando o utilizador ou bucket associado não existe.

## Corrigir e retirar buckets, objetivos e transações

`PATCH /accounts/{accountId}` — altera o nome e/ou o tipo (`SAVINGS` ou
`INVESTMENT`). Campos omitidos ficam inalterados; o saldo só muda através de
transações.

`PATCH /buckets/{bucketId}` — altera o nome e/ou corrige o montante alocado.
Campos omitidos ficam inalterados. Corrigir o montante é uma correção, não um
movimento: não cria transação nem mexe nos saldos das contas, pelo que o total
poupado não muda e só a divisão entre alocado e cash se ajusta. O montante é
não negativo, com até 17 algarismos inteiros e duas casas decimais.

`DELETE /buckets/{bucketId}` — arquiva o bucket e devolve `204 No Content`. A
linha é mantida: transações e objetivos referenciam `buckets(id)` e o histórico
tem de continuar a nomear a finalidade onde o dinheiro esteve. Um bucket
arquivado sai do dashboard e das listagens, leva os seus objetivos com ele e é
recusado em novas transações. O bucket tem de estar a zero; com dinheiro
alocado devolve `409 Conflict`, porque esse saldo desapareceria do total
alocado. Arquivar é idempotente e não existe endpoint para desarquivar.

`PATCH /goals/{goalId}` — altera o montante pretendido e/ou a data. Campos
omitidos ficam inalterados; `"goalObjective": null` remove a data (JSON Merge
Patch). O bucket é fixo: apontar um objetivo a outro bucket é outro objetivo.
`DELETE /goals/{goalId}` — devolve `204 No Content` e remove a linha; nada
referencia um objetivo. O bucket e a sua alocação não são afetados.

`POST /transactions/{transactionId}/reversal` — devolve `201 Created` com uma
transação compensatória que desfaz o efeito nos saldos e liga à original em
`reversalOf`. A original é mantida, logo o histórico mostra o erro e a correção.
A reversão troca origem e destino nos dois lados, o que transforma uma entrada
numa saída e uma transferência na transferência oposta; é datada de hoje e
passa pelo mesmo `CreateTransactionAction`, com os mesmos locks e validações.
Reverter duas vezes a mesma transação, ou reverter uma reversão, devolve
`409 Conflict`; um índice único em `reversal_of` garante isso mesmo em corrida.
Uma transação cujo bucket já foi arquivado não pode ser revertida.
