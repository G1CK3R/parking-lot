-- =====================================================================
-- Parque de Estacionamento: dados de arranque (ADR-006)
--
-- 200 lugares em 22 baias, mais a baia de manobra:
--   C01-C04  curta duração      4 x 5   =  20
--   J01-J16  jornada completa  16 x 10  = 160
--   P01-P02  permanência longa  2 x 10  =  20
--   M        baia de manobra    capacidade 10 (a profundidade máxima)
--
-- A ordem de inserção é a ordem da configuração: é por ela que as políticas
-- percorrem as baias.
--
-- Não há administrador inicial com palavra-passe conhecida. Com a tabela
-- users vazia, o programa pede para criar o primeiro administrador.
-- =====================================================================

USE parking_lot;

INSERT INTO lanes (code, capacity, stay_class, is_buffer) VALUES
    ('C01', 5, 'SHORT', FALSE),
    ('C02', 5, 'SHORT', FALSE),
    ('C03', 5, 'SHORT', FALSE),
    ('C04', 5, 'SHORT', FALSE),
    ('J01', 10, 'FULL_DAY', FALSE),
    ('J02', 10, 'FULL_DAY', FALSE),
    ('J03', 10, 'FULL_DAY', FALSE),
    ('J04', 10, 'FULL_DAY', FALSE),
    ('J05', 10, 'FULL_DAY', FALSE),
    ('J06', 10, 'FULL_DAY', FALSE),
    ('J07', 10, 'FULL_DAY', FALSE),
    ('J08', 10, 'FULL_DAY', FALSE),
    ('J09', 10, 'FULL_DAY', FALSE),
    ('J10', 10, 'FULL_DAY', FALSE),
    ('J11', 10, 'FULL_DAY', FALSE),
    ('J12', 10, 'FULL_DAY', FALSE),
    ('J13', 10, 'FULL_DAY', FALSE),
    ('J14', 10, 'FULL_DAY', FALSE),
    ('J15', 10, 'FULL_DAY', FALSE),
    ('J16', 10, 'FULL_DAY', FALSE),
    ('P01', 10, 'LONG_TERM', FALSE),
    ('P02', 10, 'LONG_TERM', FALSE),
    ('M', 10, NULL, TRUE);

-- Tarifa provisória (RN01 a RN03 por confirmar): tolerância de 15 min,
-- fracções de 15 min, 60 MZN por hora, tecto de 400 MZN por dia.
INSERT INTO tariffs (free_minutes, fraction_minutes, hourly_rate_cents, daily_cap_cents, active, created_at)
VALUES (15, 15, 6000, 40000, TRUE, NOW());

-- O primeiro talão é o número 1.
INSERT INTO park_state (id, next_ticket_no) VALUES (1, 1);
