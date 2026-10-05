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
2. [Como o parque está configurado](#como-o-parque-está-configurado)
3. [Requisitos](#requisitos)
4. [Primeira instalação](#primeira-instalação)
5. [Compilar e correr](#compilar-e-correr)
6. [Testes](#testes)
7. [Estrutura do repositório](#estrutura-do-repositório)
8. [Convenções de código](#convenções-de-código)
9. [Trabalhar em equipa](#trabalhar-em-equipa)
10. [Problemas comuns](#problemas-comuns)
11. [Documentação do projecto](#documentação-do-projecto)
12. [Equipa](#equipa)

---

## O que o sistema faz

Um operador ao balcão regista entradas e saídas de viaturas, cobra a permanência e emite talão.
O parque não tem lugares numerados: tem **baias**, corredores de estacionamento denso onde os carros
ficam uns atrás dos outros.

- **Baia em beco sem saída → pilha.** Entra e sai pelo mesmo extremo. Retirar um carro à profundidade
  *k* custa **2k movimentos**, passando pela **baia de manobra** (pilha auxiliar).
- **Baia atravessável → fila.** Entra por um lado, sai pelo outro, estritamente FIFO.
- **Filas de espera à entrada** quando o parque enche: duas filas FIFO puras (normal e preferencial),
  servidas por uma política de atendimento — é assim que se obtém prioridade sem fila de prioridade.

O problema algorítmico central é **em que baia colocar o carro que acaba de chegar**: a decisão de agora
determina quantas manobras se pagam daqui a duas horas. O sistema implementa três políticas de colocação
e compara-as por simulação.

### Funcionalidades do MVP

- Autenticação com perfis (operador e administrador)
- Registo de entrada com atribuição automática de baia
- Registo de saída por talão ou matrícula, com animação do plano de manobras
- Cálculo de tarifa (tolerância gratuita, fracção mínima, valor por hora, tecto diário) e pagamento
- Painel de ocupação por baia: topo, profundidade ocupada e espaço livre
- Fila de espera e histórico dos últimos movimentos
- Simulador de um dia de movimento, com recolha de métricas para o relatório
- Persistência do estado completo: o parque reabre exactamente como ficou

---

## Como o parque está configurado

**200 lugares em 22 baias**, mais uma baia de manobra com capacidade 10 (igual à profundidade máxima,
para que nenhum carro seja irrecuperável).

| Classe de permanência | Baias | Profundidade | Lugares |
| --- | --- | --- | --- |
| Permanência longa | 2 | 10 | 20 |
| Jornada completa | 16 | 10 | 160 |
| Curta duração | 4 | 5 | 20 |

A segregação por classe baseia-se no horário de trabalho típico em Maputo (8h–17h): o grosso das chegadas
concentra-se entre as **7h e as 9h** e as saídas entre as **16h e as 18h**.

**Invariante de colocação:** empilhar um carro apenas sobre carros que saem depois dele.

**Transbordo quando uma classe enche** — a invariante aplicada directamente:

- Curta duração sem espaço → vai para o **topo** de uma baia de jornada completa (sai antes dos de baixo)
- Permanência longa sem espaço → vai para o **fundo** de uma baia de jornada completa vazia
- Jornada completa sem espaço → só para o fundo de uma baia de curta duração vazia, nunca por cima

---

## Requisitos

| Ferramenta | Versão | Nota |
| --- | --- | --- |
| JDK | 27 para desenvolver | Compila-se sempre com `--release 17` |
| MySQL | 8.0 ou superior | Também serve MariaDB equivalente |
| Git | qualquer versão recente | |

**Não é preciso Maven, Gradle nem ligação à internet.** Todas as dependências estão versionadas em
`lib/`: o driver JDBC do MySQL, o FlatLaf e a consola do JUnit. Clonar o repositório basta para compilar.

> **Porquê `--release 17` com um JDK 27?** O bytecode gerado por omissão num JDK 27 não corre num JDK 17.
> Com `--release 17`, o compilador recusa qualquer API posterior ao Java 17 e o resultado corre em qualquer
> máquina da equipa ou do laboratório. Nenhuma funcionalidade do projecto precisa de mais do que o Java 17.

---

## Primeira instalação

### 1. Clonar

```bash
git clone <url-do-repositorio>
cd parking-lot
```

### 2. Criar a base de dados

```bash
mysql -u root -p < sql/01-schema.sql
mysql -u root -p < sql/02-seed.sql
```

- `01-schema.sql` cria as tabelas, chaves e índices.
- `02-seed.sql` carrega as 22 baias, a baia de manobra, a tabela de tarifas e o administrador inicial.

Estes dois ficheiros correm **sempre do zero**. Nunca alterem tabelas à mão: se o esquema mudar, muda-se
o `.sql`, apaga-se a base e volta a correr. É o que impede o «na minha máquina funciona».

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

> `resources/config.properties` está no `.gitignore` de propósito. **Nunca o comitem** — cada pessoa
> tem o seu. O que é versionado é o `.example`.

### 4. Compilar e arrancar

```bash
./build.sh && ./run.sh
```

---

## Compilar e correr

### Linux e macOS

```bash
./build.sh    # compila src/ e test/ para bin/
./run.sh      # arranca a aplicação
```

O que os scripts fazem por dentro:

```bash
# build.sh
find src test -name '*.java' > sources.txt
javac --release 17 -encoding UTF-8 -d bin -cp 'lib/*' @sources.txt

# run.sh
java -cp 'bin:lib/*:resources' mz.uem.eda.parking.Main
```

### Windows

```bat
build.bat
run.bat
```

Mudam duas coisas: a listagem dos ficheiros é `dir /s /b src\*.java test\*.java > sources.txt` e o
separador do classpath é `;` em vez de `:`.

A pasta `resources` **tem de entrar no classpath de execução**, senão o `ResourceBundle` não encontra
`strings_pt.properties` e a interface arranca sem textos. É o erro mais fácil de cometer nos scripts.

---

## Testes

```bash
java -jar lib/junit-platform-console-standalone-1.x.x.jar --class-path bin --scan-class-path
```

Cada TAD tem um teste que cobre o contrato, a estrutura vazia, a estrutura cheia e a ordem de saída.
As políticas de colocação são testadas sobre os mesmos cenários fixos, para poderem ser comparadas
com números em vez de opiniões.

**Nada entra no ramo principal com testes a falhar.**

---

## Estrutura do repositório

```
parking-lot/
├── build.sh  build.bat          compila para bin/
├── run.sh    run.bat            executa
├── lib/                         jars versionados (sem Maven)
├── bin/                         saída da compilação, fora do git
├── sql/                         01-schema.sql, 02-seed.sql
├── resources/                   strings_pt.properties, config, ícones
├── docs/                        relatório, maquetes, diagramas, medições
├── src/mz/uem/eda/parking/
│   ├── Main.java
│   ├── structures/              os dois TAD e as suas excepções
│   ├── algorithms/              radix sort, ordenação por duas pilhas, fusão, busca
│   ├── model/                   domínio, sem lógica de negócio
│   ├── service/                 lógica do parque
│   │   └── policy/              políticas de colocação e de atendimento
│   ├── persistence/             JDBC: guardar e repor, nada mais
│   ├── simulation/              gerador, simulador, métricas
│   └── ui/                      Swing
└── test/mz/uem/eda/parking/
```

**Camadas, com dependências só para baixo:** `ui` → `service` → `model` ← `persistence`,
com `structures` e `algorithms` por baixo de tudo. A interface nunca fala com a base de dados;
a base de dados nunca conhece regras de negócio.

O papel exacto de **cada ficheiro** está em
[Estrutura do Projecto](https://claude.ai/artifact/DrhrM9oSuV2iHCj79xgbgD).

---

## Convenções de código

- **Código em inglês, interface em português.** Classes, métodos, variáveis, tabelas e colunas em inglês;
  todo o texto visível ao utilizador vive em `resources/strings_pt.properties`. Nenhuma frase em português
  dentro de código.
- **Nomes próprios dos TAD.** As nossas interfaces chamam-se `Stack` e `Queue` e colidem com as de
  `java.util` — é propositado. Nenhum ficheiro do projecto importa `java.util.Stack` ou `java.util.Queue`.
- **Javadoc com complexidade.** Todos os métodos públicos de `structures` e `algorithms` declaram o custo:

  ```java
  /**
   * Remove o veículo que está à profundidade indicada, devolvendo o plano de manobras.
   *
   * @param depth profundidade do veículo na baia, contada a partir do topo
   * @return fila de movimentos a executar
   * @complexity O(k), k = depth — 2k movimentos no total
   */
  ```

- **Sem `synchronized` nos TAD.** Sincronização dentro de operações anunciadas como O(1) contamina a
  análise de complexidade do relatório. Toda a manipulação do domínio acontece na EDT.
- **A base de dados só guarda e repõe.** Nenhum `WHERE plate = ?` para localizar um veículo, nenhum
  `ORDER BY` para relatórios, nenhum `COUNT` para a ocupação. Procurar, ordenar e contar é trabalho das
  nossas estruturas — é isso que a cadeira avalia.
- `PreparedStatement` em todas as consultas, sem excepção.

---

## Trabalhar em equipa

### Frentes

Cada frente é dona dos seus ficheiros. Ninguém escreve na pasta de outra pessoa sem avisar.

| Frente | Ficheiros |
| --- | --- |
| Coordenação | scripts, `lib/`, `sql/`, este README, revisão das integrações |
| Estruturas | `structures/`, `algorithms/`, `test/structures/` |
| Domínio | `model/`, `persistence/`, serviços de entrada, saída e facturação |
| Políticas | `service/policy/`, `WaitingQueueService`, `test/service/policy/` |
| Interface | `ui/`, `strings_pt.properties`, maquetes |
| Simulação | `simulation/`, `docs/measurements/`, gráficos |

### Ramos

- `main` — compila sempre e tem os testes verdes
- `dev` — integração
- `feat/<frente>-<assunto>` — trabalho do dia a dia

Integrar no ramo principal **duas vezes por semana no mínimo**, mesmo incompleto, desde que compile.
Ramos com mais de uma semana de vida são o caminho mais rápido para uma semana perdida em conflitos.

### Regras

- Nada entra em `main` sem testes a passar e sem revisão de outra pessoa
- Quem encontra um erro fora da sua frente abre uma questão; o dono corrige
- Enquanto a frente de que dependes não entrega, programa-se contra a interface e testa-se com uma
  implementação de mentira — é para isso que as interfaces foram o primeiro ficheiro a existir
- Ponto de situação às segundas, quinze minutos

---

## Problemas comuns

| Sintoma | Causa | Solução |
| --- | --- | --- |
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | `lib/` fora do classpath | Usar os scripts; não invocar `java` à mão |
| `MissingResourceException: strings_pt` | `resources` fora do classpath de execução | Confirmar o `-cp` do `run` |
| Interface arranca sem textos | o mesmo | o mesmo |
| `Access denied for user` | credenciais erradas em `config.properties` | Rever utilizador, palavra-passe e privilégios |
| `Unknown database 'parking_lot'` | o `01-schema.sql` não correu | Correr os dois ficheiros de `sql/` |
| `UnsupportedClassVersionError` | compilado sem `--release 17` | Recompilar com os scripts |
| Compila em Linux e falha em Windows | separador do classpath | `;` no `.bat`, `:` no `.sh` |
| Acentos trocados no código | falta `-encoding UTF-8` | Já está nos scripts; confirmar que não foi alterado |

---

## Documentação do projecto

| Documento | O que tem |
| --- | --- |
| [Pré-relatório](https://claude.ai/artifact/SkVe3bsfwVZA9rKEr8JMMk) | O percurso de decisões e o funcionamento do MVP, passo a passo |
| [Estrutura do Projecto](https://claude.ai/artifact/DrhrM9oSuV2iHCj79xgbgD) | Arquitectura, papel de cada ficheiro, esquema da base, plano até à entrega |
| [Página do projecto no Notion](https://app.notion.com/p/3f0bafe4467a8178b180ebc21e573df6) | Requisitos, decisões abertas e fechadas, estruturas e algoritmos |

### Calendário

Quatro fases, cada uma fechada por uma porta que ou está passada ou não está:

| Fase | Datas | Porta |
| --- | --- | --- |
| 1 · Fundações | 5–18 Out | Compila nas cinco máquinas e os testes dos TAD passam |
| 2 · Motor do parque | 19 Out–1 Nov | Entrada e saída por teste; o plano de manobras repõe a ordem |
| 3 · Interface, persistência e medições | 26 Out–9 Nov | MVP ponta a ponta e CSV com as três políticas comparadas |
| 4 · Relatório e defesa | 9–16 Nov | Relatório entregue e demonstração ensaiada |

A data de entrega aponta para as Aulas 31–32 e ainda está por confirmar com o docente.

---

## Equipa

Grupo de cinco, cadeira de Estruturas de Dados e Algoritmos, UEM.
Chefe de grupo: Agnelo Vilanculo.

Trabalho académico. O código não se destina a utilização comercial.