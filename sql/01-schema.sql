-- =====================================================================
-- Parque de Estacionamento: esquema da base de dados (MySQL 8 / MariaDB)
--
-- Corre sempre do zero: apaga a base e volta a criá-la.
--   mysql -u root -p < sql/01-schema.sql
--   mysql -u root -p < sql/02-seed.sql
--
-- A base de dados só guarda e repõe o estado do parque. Nenhuma pesquisa,
-- ordenação ou contagem do domínio é feita em SQL: isso é trabalho das
-- pilhas, das filas e dos algoritmos do projecto. Os WHERE escolhem que
-- linhas repor (por chave ou por estado) e os ORDER BY só servem para
-- reconstruir uma estrutura pela ordem em que estava.
--
-- Dinheiro em centavos de metical (BIGINT). Enumerados guardados pelo nome
-- da constante Java (VARCHAR), para o esquema não repetir as listas.
-- =====================================================================

DROP DATABASE IF EXISTS parking_lot;
CREATE DATABASE parking_lot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE parking_lot;

-- Utilizadores do sistema: hash PBKDF2, sal e iterações em colunas separadas.
-- Apagar um utilizador é desactivá-lo (active = FALSE): os pagamentos que
-- cobrou continuam a apontar para ele. failed_attempts conta as palavras-passe
-- erradas seguidas; locked_until diz até quando a conta está bloqueada (RF03).
CREATE TABLE users (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    username         VARCHAR(50)  NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    salt             VARCHAR(50)  NOT NULL,
    iterations       INT          NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB;

-- Viaturas, com a matrícula normalizada (maiúsculas, sem espaços nem hífenes)
-- e o último tipo registado. Não há tabela de clientes: o parque trabalha com
-- condutores ocasionais, e a avença guarda o nome e o telefone do cliente.
CREATE TABLE vehicles (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    plate         VARCHAR(20) NOT NULL,
    vehicle_type  VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vehicles_plate (plate)
) ENGINE = InnoDB;

-- Baias, pela ordem da configuração (a ordem dos id). A de manobra não tem classe.
CREATE TABLE lanes (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    code        VARCHAR(10) NOT NULL,
    capacity    INT         NOT NULL,
    stay_class  VARCHAR(20) NULL,
    is_buffer   BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lanes_code (code),
    CHECK (capacity >= 1)
) ENGINE = InnoDB;

-- Sessões de estacionamento: o talão é o ticket_no. status: ACTIVE (no
-- parque), CLOSED (saiu e pagou) ou VOIDED (entrada desfeita pelo operador).
CREATE TABLE parking_sessions (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_no          INT         NOT NULL,
    vehicle_id         BIGINT      NOT NULL,
    lane_id            BIGINT      NOT NULL,
    stay_class         VARCHAR(20) NOT NULL,
    estimated_minutes  INT         NOT NULL,
    entry_at           DATETIME    NOT NULL,
    exit_at            DATETIME    NULL,
    moves_count        INT         NOT NULL DEFAULT 0,
    status             VARCHAR(10) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sessions_ticket (ticket_no),
    CONSTRAINT fk_sessions_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_sessions_lane FOREIGN KEY (lane_id) REFERENCES lanes (id)
) ENGINE = InnoDB;

-- O conteúdo de cada pilha. position 1 é o fundo; o topo tem a maior posição.
-- Ao repor, empilha-se por position crescente e a pilha fica como estava.
CREATE TABLE lane_slots (
    lane_id     BIGINT NOT NULL,
    position    INT    NOT NULL,
    session_id  BIGINT NOT NULL,
    PRIMARY KEY (lane_id, position),
    UNIQUE KEY uk_slots_session (session_id),
    CONSTRAINT fk_slots_lane FOREIGN KEY (lane_id) REFERENCES lanes (id),
    CONSTRAINT fk_slots_session FOREIGN KEY (session_id) REFERENCES parking_sessions (id)
) ENGINE = InnoDB;

-- Tarifas. Mudar preços é inserir uma tarifa nova e desactivar a anterior:
-- há sempre exactamente uma activa. As antigas ficam, porque os pagamentos
-- apontam para a tarifa com que foram calculados (ADR-031). Além do valor
-- por hora de dia, a tarifa tem o valor por hora de noite (20h às 7h), o
-- desconto ao fim-de-semana em percentagem, a multa por talão perdido e o
-- valor da avença mensal (RF16, RF17).
CREATE TABLE tariffs (
    id                    BIGINT   NOT NULL AUTO_INCREMENT,
    free_minutes          INT      NOT NULL,
    fraction_minutes      INT      NOT NULL,
    hourly_rate_cents     BIGINT   NOT NULL,
    daily_cap_cents       BIGINT   NOT NULL,
    night_rate_cents      BIGINT   NOT NULL,
    weekend_discount_pct  INT      NOT NULL,
    lost_ticket_cents     BIGINT   NOT NULL,
    monthly_pass_cents    BIGINT   NOT NULL,
    active                BOOLEAN  NOT NULL DEFAULT TRUE,
    created_at         DATETIME NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- Pagamentos. Gravados na mesma transacção que fecha a sessão: um por
-- sessão. Cada um guarda a tarifa com que foi calculado (ADR-031).
-- lost_ticket_cents é a parte do valor que é multa por talão perdido. O
-- método PASS quer dizer que a estadia foi coberta por uma avença.
CREATE TABLE payments (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    session_id         BIGINT      NOT NULL,
    tariff_id          BIGINT      NOT NULL,
    amount_cents       BIGINT      NOT NULL,
    lost_ticket_cents  BIGINT      NOT NULL DEFAULT 0,
    method             VARCHAR(10) NOT NULL,
    paid_at            DATETIME    NOT NULL,
    user_id            BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_session (session_id),
    CONSTRAINT fk_payments_session FOREIGN KEY (session_id) REFERENCES parking_sessions (id),
    CONSTRAINT fk_payments_tariff FOREIGN KEY (tariff_id) REFERENCES tariffs (id),
    CONSTRAINT fk_payments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CHECK (amount_cents >= 0)
) ENGINE = InnoDB;

-- Reservas. Repostas por ordem de criação, que é a ordem de expiração.
CREATE TABLE reservations (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    plate              VARCHAR(20) NOT NULL,
    stay_class         VARCHAR(20) NOT NULL,
    estimated_minutes  INT         NOT NULL,
    created_at         DATETIME    NOT NULL,
    expires_at         DATETIME    NOT NULL,
    status             VARCHAR(12) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- Avenças mensais (RF16): uma matrícula, um mês de calendário (valid_from é o
-- primeiro dia e valid_until o último), pagas ao balcão. O pagamento da
-- avença conta na receita do dia em que foi vendida.
CREATE TABLE monthly_passes (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    customer_name  VARCHAR(100) NOT NULL,
    phone          VARCHAR(30)  NULL,
    plate          VARCHAR(20)  NOT NULL,
    valid_from     DATE         NOT NULL,
    valid_until    DATE         NOT NULL,
    amount_cents   BIGINT       NOT NULL,
    method         VARCHAR(10)  NOT NULL,
    paid_at        DATETIME     NOT NULL,
    user_id        BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_passes_user FOREIGN KEY (user_id) REFERENCES users (id),
    CHECK (amount_cents >= 0)
) ENGINE = InnoDB;

-- Registo de auditoria (RF23): quem fez o quê, e quando, nas operações
-- sensíveis. username guarda o nome escrito (num login falhado pode não
-- existir). detail guarda os valores da operação separados por |.
CREATE TABLE audit_log (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    at        DATETIME     NOT NULL,
    username  VARCHAR(50)  NULL,
    action    VARCHAR(30)  NOT NULL,
    detail    VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- Estado global do parque numa única linha (id = 1): por agora, o número do
-- próximo talão, para a numeração continuar depois de reabrir.
CREATE TABLE park_state (
    id              TINYINT NOT NULL,
    next_ticket_no  INT     NOT NULL,
    PRIMARY KEY (id),
    CHECK (id = 1)
) ENGINE = InnoDB;
