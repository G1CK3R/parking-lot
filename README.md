# Parque de Estacionamento

Sistema desktop de gestão de um parque de estacionamento denso, escrito em Java com Swing.
Trabalho semestral de **Estruturas de Dados e Algoritmos** — Faculdade de Engenharia, UEM.

> **Restrição da cadeira: apenas pilha e fila.** Não há tabelas de dispersão, árvores, grafos nem
> filas de prioridade. A lista ligada é permitida só como suporte interno de implementação dos dois
> TAD. O parque está modelado de forma a que as duas estruturas sejam intrínsecas ao problema, não
> acrescentadas por cima de um CRUD.

---

## Índice

1. [O que o sistema faz](#o-que-o-sistema-faz)
2. [Onde estão as pilhas e as filas](#onde-estão-as-pilhas-e-as-filas)
3. [Como o parque está configurado](#como-o-parque-está-configurado)
4. [Políticas e resultados da simulação](#políticas-e-resultados-da-simulação)
5. [Requisitos de software](#requisitos-de-software)
6. [Primeira instalação](#primeira-instalação)
7. [Correr o programa](#correr-o-programa)
8. [Usar o programa](#usar-o-programa)
9. [Testes](#testes)
10. [Arquitectura](#arquitectura)
11. [Estrutura do repositório](#estrutura-do-repositório)
12. [Convenções de código](#convenções-de-código)
13. [Trabalhar em equipa](#trabalhar-em-equipa)
14. [Problemas comuns](#problemas-comuns)
15. [Limitações conhecidas](#limitações-conhecidas)
16. [Documentação e calendário](#documentação-e-calendário)
17. [Equipa](#equipa)

---

## O que o sistema faz

Um operador ao balcão regista entradas e saídas de viaturas, cobra a permanência e emite talão.
O parque não tem lugares numerados: tem **baias**, corredores de estacionamento denso onde os carros
ficam uns atrás dos outros.

- **Cada baia é uma pilha.** As baias são becos sem saída: o carro entra e sai pelo mesmo extremo.
  Tirar um carro à profundidade *k* custa **2k manobras**: os *k* carros de cima passam para a
  **baia de manobra** (outra pilha) e depois voltam, pela mesma ordem.
- **Filas de espera à entrada** quando o parque enche: duas filas FIFO puras, normal e preferencial,
  servidas por uma política de atendimento. É assim que se tem prioridade sem fila de prioridade.
- **Reservas** guardam um lugar durante 30 minutos. Como a tolerância é igual para todas, a ordem de
  criação é a ordem de expiração, e uma fila simples resolve a expiração.

O problema algorítmico central é **em que baia colocar o carro que acaba de chegar**: a decisão de agora
determina quantas manobras se pagam daqui a umas horas. O sistema tem quatro políticas de colocação e
compara-as por simulação.

### Funcionalidades

| Onde | O quê | Requisitos |
| --- | --- | --- |
| Arranque | Login com utilizador e palavra-passe (PBKDF2); na primeira vez, cria o administrador | RF01, RF02 |
| Ecrã principal | O parque visto de cima: as 22 baias, cada carro com a sua cor, a classe da baia, o topo da pilha e a ocupação; dica do rato com talão, entrada e manobras | RF04, RF05 |
| Painel lateral | As duas filas de espera, com o tempo de espera, e os últimos 20 movimentos | RF05 |
| Operações → Entrada (F2) | Matrícula, tipo e duração declarada; atribuição automática da baia; talão; animação do carro até à baia; fila de espera se o parque estiver cheio | RF06 |
| Operações → Saída (F3) | Procura por matrícula ou talão; valor a pagar; método de pagamento; animação das manobras; recibo; entra o primeiro da fila de espera | RF07, RF08, RF09, RF14, RF15 |
| Operações → Reservas (F5) | Reservas activas com o tempo que falta, nova reserva, cancelar; o check-in é a entrada normal | RF10, RF11, RF12 |
| Operações → Fecho de caixa (F4) | O que quem está na sessão cobrou desde que entrou, por método de pagamento | RF21 |
| Sessão | Mudar a própria palavra-passe; terminar sessão | RF03 |
| Administração → Utilizadores | Criar operadores e administradores, repor palavras-passe, retirar utilizadores | RF22 |
| Administração → Tarifa | Tolerância, fracção, valor por hora e tecto diário, com exemplos antes de gravar | RF13 |
| Administração → Relatórios | Receita por dia, por operador e por método; lista de pagamentos em três ordens; exportar CSV | RF18, RF20 |
| Administração → Simulação | Compara as 16 combinações de políticas em dias simulados; gráficos de manobras e de ocupação; exportar CSV | RF19 |

Tudo fica gravado em MySQL: fechar e voltar a abrir repõe o parque exactamente como estava, com cada
carro na mesma posição de cada baia.

---

## Onde estão as pilhas e as filas

As duas estruturas estão em `structures/`, escritas de raiz: `Stack` e `Queue` são interfaces nossas,
com `ArrayStack`, `LinkedStack` e `LinkedQueue` por trás.

| Estrutura | Onde | Porquê |
| --- | --- | --- |
| Pilha | Cada baia de estacionamento (`Lane`) | Beco sem saída: o último a entrar é o primeiro a sair |
| Pilha | Baia de manobra | Guarda os carros de cima enquanto se tira o de baixo; voltam pela ordem inversa, que repõe a original |
| Fila | Plano de manobras (`RetrievalPlan`) | A saída calcula-se toda de uma vez; a animação consome os movimentos pela ordem |
| Fila | Filas de espera, normal e preferencial | Quem chega primeiro entra primeiro, em cada fila |
| Fila | Reservas | Tolerância constante ⇒ a da frente é sempre a próxima a expirar |
| Fila | Histórico de pagamentos, últimos movimentos, utilizadores | Crescem pelo fim e lêem-se pela ordem do tempo |
| Fila | Chegadas da simulação | A «fila de eventos» do dia, ordenada por minuto |

### Algoritmos sobre pilhas e filas

Em `algorithms/`, cada um com a complexidade no Javadoc e com testes:

| Algoritmo | Complexidade | Onde é usado |
| --- | --- | --- |
| Radix sort com 10 filas (`QueueRadixSort`) | O(d·n), sem comparações | Relatórios (pela hora, talão, valor, operador, método) e chegadas da simulação |
| Ordenação com duas pilhas (`TwoStackSort`) | O(n²) | Pagamentos do turno no fecho de caixa, com o número de comparações no ecrã |
| Inversão com uma pilha | O(n) | Ordem por valor do maior para o menor, nos relatórios |
| Totais por grupos seguidos | O(n) depois de ordenar | Receita por dia, operador e método, sem tabela de dispersão |
| Busca sequencial (`SequentialSearch`) | O(n) | Procurar um carro por matrícula ou talão; dá a profundidade *k* |
| Fusão de duas filas ordenadas (`QueueMerge`) | O(n + m) | Disponível e testada, para juntar históricos já ordenados |

---

## Como o parque está configurado

**200 lugares em 22 baias**, mais uma baia de manobra com capacidade 10 (igual à profundidade máxima,
para que nenhum carro fique irrecuperável).

| Classe de permanência | Código | Baias | Profundidade | Lugares |
| --- | --- | --- | --- | --- |
| Curta duração (até 2 h) | C01–C04 | 4 | 5 | 20 |
| Jornada completa (até 24 h) | J01–J16 | 16 | 10 | 160 |
| Permanência longa (mais de 24 h) | P01–P02 | 2 | 10 | 20 |
| Baia de manobra | M | 1 | 10 | — |

A classe de cada carro sai da **duração que o condutor declara à entrada**. A segregação baseia-se no
horário de trabalho típico em Maputo (8h–17h): o grosso das chegadas concentra-se entre as **7h e as
9h** e as saídas entre as **16h e as 18h**.

**Tarifa de origem** (muda-se em Administração → Tarifa): 15 minutos de tolerância gratuita, fracções
de 15 minutos, 60 MZN por hora e tecto diário de 400 MZN. Os valores guardam-se em centavos, num
`long`, para as contas serem exactas.

---

## Políticas e resultados da simulação

**Colocação** (em que baia fica o carro), `service/policy/`:

| Política | Ideia |
| --- | --- |
| `FirstWithSpace` | A primeira baia com espaço. Linha de base |
| `LeastOccupied` | A baia com menos carros |
| `ByStayClass` | A baia da sua classe, empilhando só sobre quem sai depois (por classe) |
| `ByDeparture` | A mesma regra, mas com a **hora de saída declarada**: encaixe mais justo, depois baia vazia, e em último recurso a baia cujo topo sai mais tarde. **É a que o programa usa** |

**Atendimento** (quem sai das filas de espera): `StrictPriority` (preferencial sempre primeiro) e
`RoundRobin(3)` (três preferenciais por cada normal; o programa usa esta).

**Resultados** (`docs/measurements/simulation.csv`, 20 dias por combinação, 260 carros por dia, 15% de
preferenciais, erro de ±20% na duração declarada), manobras por saída:

| Política | Configuração real | Homogénea (20 × 10) |
| --- | --- | --- |
| `FirstWithSpace` | 5,31 | 5,53 |
| `LeastOccupied` | 4,94 | 5,14 |
| `ByStayClass` | 5,06 | 5,89 |
| **`ByDeparture`** | **4,31** | **4,61** |

A `ByStayClass` perde para a `LeastOccupied` porque só distingue três classes e 70% dos carros são de
jornada completa: entre eles, a regra deixa empilhar qualquer um sobre qualquer um. A `ByDeparture`
aplica a mesma regra com horas e faz 19% menos manobras do que a linha de base (53% menos com 200 carros
por dia, quando não há fila). A política de atendimento quase não muda as manobras; muda a espera
máxima na fila preferencial.

Para regenerar o CSV: configuração «Simulação (gera o CSV)» no VS Code, ou Administração → Simulação
no programa.

---

## Requisitos de software

| Ferramenta | Versão | Nota |
| --- | --- | --- |
| JDK | 17 ou mais recente | O código só usa o que existe no Java 17 |
| VS Code | qualquer versão recente | Com o *Extension Pack for Java* |
| MySQL ou MariaDB | MySQL 8.0, ou o MariaDB do XAMPP | |
| Git | qualquer versão recente | |

**Não é preciso Maven, Gradle nem ligação à internet.** Todas as dependências estão versionadas em
`lib/`: o driver JDBC do MySQL, o FlatLaf (aspecto da interface) e o JUnit. A configuração do VS Code
também está versionada em `.vscode/`, por isso abrir a pasta basta para correr.

---

## Primeira instalação

### 1. Clonar

```bash
git clone https://github.com/G1CK3R/parking-lot.git
cd parking-lot
```

### 2. Criar a base de dados

Na linha de comandos:

```bash
mysql -u root -p < sql/01-schema.sql
mysql -u root -p < sql/02-seed.sql
```

Com o XAMPP: arrancar o MySQL no painel do XAMPP, abrir o phpMyAdmin, separador **Importar**, e importar
primeiro `sql/01-schema.sql` e depois `sql/02-seed.sql`.

- `01-schema.sql` apaga e cria a base `parking_lot`, com as tabelas, chaves e índices.
- `02-seed.sql` carrega as 22 baias, a baia de manobra, a tarifa de origem e o número do primeiro talão.

Estes dois ficheiros correm **sempre do zero** e apagam os dados gravados. Nunca alterem tabelas à mão:
se o esquema mudar, muda-se o `.sql` e volta-se a correr. É o que impede o «na minha máquina funciona».

Para não usar o `root` no dia-a-dia, criem um utilizador próprio:

```sql
CREATE USER 'parking'@'localhost' IDENTIFIED BY 'a-vossa-palavra-passe';
GRANT ALL PRIVILEGES ON parking_lot.* TO 'parking'@'localhost';
GRANT ALL PRIVILEGES ON parking_lot_test.* TO 'parking'@'localhost';
```

A segunda permissão é para os testes, que criam e apagam a base `parking_lot_test`.

### 3. Configurar a ligação

```bash
cp resources/config.properties.example resources/config.properties
```

Editem o ficheiro copiado com os vossos dados:

```properties
db.url=jdbc:mysql://localhost:3306/parking_lot
db.user=parking
db.password=a-vossa-palavra-passe
```

No XAMPP, o utilizador de origem é `root` sem palavra-passe (`db.password=` vazio).

> `resources/config.properties` está no `.gitignore` de propósito. **Nunca o comitem**: cada pessoa
> tem o seu. O que é versionado é o `.example`.

### 4. Primeiro arranque

A base começa sem utilizadores, de propósito: o seed não traz nenhuma palavra-passe conhecida. No
primeiro arranque, o programa pede para **criar o administrador** (nome e palavra-passe com pelo menos 8
caracteres, escrita duas vezes) e entra com ele. Os operadores criam-se depois em Administração →
Utilizadores.

---

## Correr o programa

O projecto corre a partir do VS Code, igual em Linux, Windows e macOS.

1. **File → Open Folder…** e escolher a pasta `parking-lot` (a raiz, não `src`).
2. Na primeira vez, aceitar a sugestão de instalar o *Extension Pack for Java* e esperar que a barra de
   estado deixe de dizer «Java: Loading».
3. No separador **Run and Debug**, escolher «Parque de Estacionamento» e carregar em F5. Também serve
   abrir `src/mz/uem/eda/parking/Main.java` e carregar em **Run** por cima do `main`.

O VS Code compila sozinho cada vez que se grava um ficheiro: não há nenhum passo de compilação à mão.
Os erros aparecem sublinhados no código e no separador *Problems*.

A configuração está em `.vscode/` e é igual para todos:

- `settings.json`: `src` e `test` são as pastas de código, os jars de `lib/` entram no classpath,
  ficheiros em UTF-8
- `launch.json`: duas configurações, «Parque de Estacionamento» (o programa) e «Simulação (gera o
  CSV)» (o `Simulator.main`, que escreve `docs/measurements/simulation.csv`); as duas arrancam na raiz
  do projecto e com `resources/` no classpath
- `extensions.json`: sugere o *Extension Pack for Java* a quem abre a pasta

**Não alterem estes três ficheiros na vossa máquina.** Se for preciso mudar alguma coisa, muda-se no
repositório para todos.

Se a base de dados não abrir, o programa diz porquê numa mensagem e fecha: falta o
`config.properties`, o MySQL não responde, as credenciais estão erradas, a base não foi criada ou o
esquema está desactualizado. Ver [Problemas comuns](#problemas-comuns).

---

## Usar o programa

**Atalhos:** F2 entrada · F3 saída · F4 fecho de caixa · F5 reservas · Esc salta a animação.

**Uma entrada.** F2, matrícula, tipo de viatura e quanto tempo o condutor diz que fica. O programa
escolhe a baia, anima o carro desde a entrada e mostra o talão. Se o parque estiver cheio, o carro fica
na fila de espera e aparece no painel lateral. Se a matrícula tiver reserva, entra com a duração da
reserva.

**Uma saída.** F3, matrícula ou número do talão, Procurar. Aparece o valor a pagar, calculado à hora
da procura. Escolhe-se o método de pagamento e carrega-se em «Cobrar e dar saída». A animação mostra
cada manobra: os carros de cima vão para a baia de manobra, o carro sai, e os outros voltam pela mesma
ordem. No fim aparece o recibo e, se houver alguém à espera, ele entra.

**Durante uma animação** as operações ficam bloqueadas; o parque real já está no estado final, e a
animação é só a reprodução. No fim, a animação confere que reproduziu exactamente o estado real.

**Perfis.** O operador vê Sessão e Operações. O administrador vê também o menu Administração. O
administrador não se pode retirar a si próprio nem retirar o último administrador.

---

## Testes

No separador **Testing** do VS Code (o ícone do frasco na barra lateral) aparecem todos os testes de
`test/`. O botão ▶ no topo corre-os todos; também se pode correr só uma classe ou um teste.

São **292 testes**, todos a passar:

| Pasta | O que testa |
| --- | --- |
| `structures/` | O contrato de cada TAD: vazio, cheio, ordem de saída, excepções |
| `algorithms/` | Radix sort, duas pilhas, fusão e busca sequencial, com casos aleatórios |
| `service/` | Entrada, saída e plano de manobras (2k), tarifa, filas de espera, reservas, autenticação, relatórios e a `ParkingDesk` inteira |
| `service/policy/` | As quatro políticas de colocação e as duas de atendimento, sobre cenários fixos |
| `persistence/` | Gravar e repor o parque, os utilizadores, as tarifas e os pagamentos |
| `simulation/` | Gerador de chegadas, métricas (incluindo a ocupação), simulador e comparação |

**Os testes da base de dados precisam do MySQL ligado.** Usam as credenciais do `config.properties` e
criam do zero uma base à parte, `parking_lot_test`, a partir dos ficheiros de `sql/`; a base do
programa não é tocada. Sem MySQL, ou sem permissão para criar essa base, esses testes aparecem como
saltados (não como falhados).

**Nada entra no `master` com testes a falhar.**

---

## Arquitectura

Camadas, com dependências só para baixo:

```
ui  ──►  service (ParkingDesk)  ──►  persistence (JDBC)
              │                            │
              ▼                            ▼
            model  ◄────────────────────────
              │
              ▼
      structures, algorithms
```

- **A interface fala só com a `ParkingDesk`** (ADR-032), o balcão: uma classe da camada `service` com
  um método por botão. Não conhece baias, pilhas, filas nem a base de dados. A única excepção é o ecrã
  de simulação, que fala com a camada `simulation`, uma ferramenta de medição à parte.
- **A `ParkingDesk` chama os serviços pela ordem certa e grava o resultado.** Uma saída, por exemplo:
  cobrar, tirar o carro com as manobras, gravar numa transacção, juntar o pagamento ao histórico e
  deixar entrar o primeiro da fila de espera.
- **A hora vem sempre de fora**, como parâmetro. A interface passa a hora actual; os testes passam a
  hora que quiserem.
- **A base de dados só guarda e repõe.** Procurar, ordenar e contar é trabalho das nossas estruturas.
- **A animação não decide nada.** O serviço faz a operação inteira de uma vez e devolve o plano de
  movimentos; a interface reproduz o plano sobre uma fotografia do parque tirada antes.
- **A simulação corre num `SwingWorker`**, com baias, serviços e políticas próprios: não toca no parque
  real.

---

## Estrutura do repositório

```
parking-lot/
├── .vscode/                     configuração partilhada do VS Code
├── lib/                         jars versionados (sem Maven)
├── bin/                         .class gerados pelo VS Code, fora do git
├── sql/                         01-schema.sql, 02-seed.sql
├── resources/                   strings_pt.properties, config.properties.example
├── docs/
│   ├── measurements/            simulation.csv, gerado pelo simulador
│   ├── report/                  relatório
│   ├── diagrams/                diagramas
│   └── mockups/                 maquetes
├── src/mz/uem/eda/parking/
│   ├── Main.java                arranque: configuração, políticas, login
│   ├── structures/              Stack, Queue e implementações; excepções
│   ├── algorithms/              radix sort, duas pilhas, fusão, busca sequencial
│   ├── model/                   Lane, ParkingSession, Vehicle, Tariff, Payment, Reservation, User, Move, RetrievalPlan…
│   ├── service/                 ParkingDesk e os serviços (entrada, saída, tarifa, espera, reservas, autenticação, relatórios)
│   │   └── policy/              políticas de colocação e de atendimento
│   ├── persistence/             repositórios JDBC; security/ com o PBKDF2
│   ├── simulation/              gerador de chegadas, simulador, métricas, comparação
│   └── ui/                      janela principal, painel do parque, painel lateral, animação, textos
│       ├── dialogs/             todos os diálogos e os dois gráficos
│       └── theme/               cores e espaçamentos
└── test/mz/uem/eda/parking/     os testes, pela mesma árvore de pacotes
```

O papel exacto de **cada ficheiro** está em
[Estrutura do Projecto](https://claude.ai/artifact/DrhrM9oSuV2iHCj79xgbgD).

---

## Convenções de código

- **Código em inglês, interface em português.** Classes, métodos, variáveis, tabelas e colunas em inglês;
  Javadoc e comentários em português; todo o texto visível ao utilizador vive em
  `resources/strings_pt.properties` e lê-se com `Texts.get` ou `Texts.format`. Nenhuma frase em
  português dentro do código da interface. Nesses textos não se usa o apóstrofo (o `MessageFormat`
  trata-o à parte).
- **Nomes próprios dos TAD.** As nossas interfaces chamam-se `Stack` e `Queue` e colidem com as de
  `java.util`: é propositado. Nenhum ficheiro do domínio importa `java.util.Stack`, `java.util.Queue`,
  listas ou mapas. A interface pode usar colecções do `java.util` para os modelos do Swing (ADR-028).
- **Javadoc com complexidade.** Os métodos públicos de `structures`, `algorithms` e dos serviços
  declaram o custo com `@complexity`:

  ```java
  /**
   * Tira o carro com este talão e devolve o plano de manobras.
   *
   * @param ticketNumber o número do talão
   * @param exitTime a hora de saída
   * @return o plano: 2k manobras para um carro à profundidade k, mais a saída
   * @complexity O(n + k): procurar o carro e fazer as 2k manobras
   */
  ```

- **Sem expressões lambda.** Usam-se classes anónimas (`new ActionListener() { … }`), como nas aulas.
- **Sem `hashCode()`.** As classes do modelo redefinem `equals` mas não usam tabelas de dispersão; os
  avisos de compilação por isso são conhecidos e aceites.
- **Composição em vez de herança no Swing.** As janelas e diálogos têm um `JFrame` ou `JDialog` lá
  dentro, em vez de os estenderem. Só os componentes desenhados à mão (`ParkPanel` e os dois gráficos)
  estendem `JPanel` ou `JComponent`.
- **Sem `synchronized` nos TAD.** Sincronização dentro de operações anunciadas como O(1) contamina a
  análise de complexidade. Toda a manipulação do domínio acontece na EDT.
- **Valores em centavos, num `long`.** Nunca `double` para dinheiro.
- `PreparedStatement` em todas as consultas, sem excepção.

O projecto compila com **7 avisos conhecidos**: 5 de `equals` sem `hashCode` (de propósito), 1 de um
construtor de `Locale` deprecado em `Texts` e 1 de tipo genérico em bruto no `QueueRadixSort`.

---

## Trabalhar em equipa

### Frentes

Cada frente é dona dos seus ficheiros. Ninguém escreve na pasta de outra pessoa sem avisar.

| Frente | Ficheiros |
| --- | --- |
| Coordenação | `.vscode/`, `lib/`, `sql/`, este README, revisão das integrações |
| Estruturas | `structures/`, `algorithms/` e os seus testes |
| Domínio | `model/`, `persistence/`, serviços de entrada, saída, tarifa e reservas |
| Políticas | `service/policy/`, `WaitingQueueService` e os seus testes |
| Interface | `ui/`, `strings_pt.properties`, maquetes |
| Simulação | `simulation/`, `docs/measurements/`, gráficos |

### Ramos

- `master`: compila sempre e tem os testes verdes
- `feat/<frente>-<assunto>`: trabalho do dia-a-dia

### Regras

- Nada entra em `master` sem testes a passar e sem revisão de outra pessoa
- **Antes de cada commit, `git status`**: confirmem que todos os ficheiros que mudaram estão lá, e que
  nada foi parar à pasta errada (um teste em `src/` em vez de `test/`, por exemplo)
- Quem encontra um erro fora da sua frente avisa o dono; o dono corrige
- Ponto de situação às segundas, quinze minutos

---

## Problemas comuns

| Sintoma | Causa | Solução |
| --- | --- | --- |
| Ao arrancar: «Falta a configuração da base de dados» | não existe `resources/config.properties` | Copiar o `.example` e preencher (passo 3) |
| Ao arrancar: «O servidor MySQL não responde» | o MySQL está desligado, ou a URL está errada | Ligar o MySQL (no XAMPP, *Start* no MySQL); rever `db.url` |
| Ao arrancar: «O MySQL recusou o utilizador ou a palavra-passe» | credenciais erradas em `config.properties` | Rever `db.user` e `db.password` |
| Ao arrancar: «A base de dados ainda não foi criada» | os ficheiros de `sql/` não correram | Correr `01-schema.sql` e depois `02-seed.sql` |
| Ao arrancar: «A base de dados está desactualizada» | o esquema mudou desde a última vez | Correr de novo os dois ficheiros (apaga os dados) |
| Imports de `structures` ou do JUnit sublinhados a vermelho | pasta errada aberta, ou o Java ainda a carregar | Abrir a raiz `parking-lot`; esperar pelo fim de «Java: Loading»; se persistir, *Java: Clean Java Language Server Workspace* |
| `duplicate class` ao compilar | o mesmo ficheiro em `src/` e em `test/` | Apagar a cópia que está na pasta errada |
| Não aparece o botão **Run** | falta o *Extension Pack for Java* | Instalar a extensão e reabrir a pasta |
| A interface mostra textos entre pontos de exclamação (`!chave!`) | falta uma chave em `strings_pt.properties`, ou o ficheiro não é encontrado | Arrancar com a configuração do `launch.json`; confirmar que o `strings_pt.properties` está completo |
| Os testes da base de dados aparecem saltados | MySQL desligado, ou sem permissão para criar `parking_lot_test` | Ligar o MySQL; dar a permissão (passo 2) |
| Acentos trocados no código | ficheiro gravado fora de UTF-8 | Barra de estado → codificação → *Save with Encoding* → UTF-8 |
| Compila numa máquina e falha noutra | uso de uma API posterior ao Java 17 | Trocar pela equivalente do Java 17 |

---

## Limitações conhecidas

Decisões assumidas, a defender no relatório:

- **Um só posto de atendimento.** A `ParkingDesk` guarda um utilizador autenticado de cada vez.
- **As filas de espera não são gravadas.** Ao reabrir começam vazias: quem esperava já não está à porta.
- **A procura por matrícula ou talão é O(n).** Com 200 lugares, o pior caso são 200 comparações.
- **Cancelar ou expirar uma reserva liberta o lugar, mas não chama o primeiro da fila de espera**; só
  uma saída o faz.
- **A entrada não pergunta se o cliente é preferencial**: no programa, todos vão para a fila normal, e a
  fila preferencial só se vê na simulação.
- **Pagamentos só registados**: numerário, M-Pesa, e-Mola e cartão, sem ligação a nenhum serviço de
  pagamento (ADR-027).
- **Não há desfazer**, avenças mensais, log de auditoria nem bloqueio da conta após tentativas falhadas.

---

## Documentação e calendário

| Documento | O que tem |
| --- | --- |
| [Pré-relatório](https://claude.ai/artifact/SkVe3bsfwVZA9rKEr8JMMk) | O percurso de decisões e o funcionamento do MVP, passo a passo |
| [Estrutura do Projecto](https://claude.ai/artifact/DrhrM9oSuV2iHCj79xgbgD) | Arquitectura, papel de cada ficheiro, esquema da base, plano até à entrega |
| [Página do projecto no Notion](https://app.notion.com/p/3f0bafe4467a8178b180ebc21e573df6) | Requisitos, decisões (ADR) abertas e fechadas, estruturas e algoritmos, registo de cada passo |
| `docs/measurements/simulation.csv` | Uma linha por dia simulado: manobras, pico, esperas, ocupação |

### Calendário

| Fase | Datas | Porta | Estado |
| --- | --- | --- | --- |
| 1 · Fundações | 5–18 Out | Compila em todas as máquinas e os testes dos TAD passam | Concluída |
| 2 · Motor do parque | 19 Out–1 Nov | Entrada e saída por teste; o plano de manobras repõe a ordem | Concluída |
| 3 · Interface, persistência e medições | 26 Out–9 Nov | MVP ponta a ponta e CSV com as políticas comparadas | MVP concluído a 27 Out; a fechar o resto do código |
| 4 · Relatório e defesa | 9–16 Nov | Relatório entregue e demonstração ensaiada noutra máquina | Começa quando o código fechar |

A data de entrega aponta para as Aulas 31–32 e ainda está por confirmar com o docente.

---

## Equipa

Grupo de cinco, cadeira de Estruturas de Dados e Algoritmos, UEM.
Chefe de grupo: Agnelo Vilanculo.

Trabalho académico. O código não se destina a utilização comercial.