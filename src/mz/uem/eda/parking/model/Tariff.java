package mz.uem.eda.parking.model;


/**
 * A tabela de preços em vigor (RF13): tolerância gratuita, fracção mínima de
 * cobrança, valor por hora e tecto diário. Desde 27 Out tem também os preços
 * por período e as multas (RF16, RF17): valor por hora de noite, desconto ao
 * fim-de-semana, multa por talão perdido e valor da avença mensal.
 *
 * <p><b>Noite e fim-de-semana.</b> A noite vai das {@value #NIGHT_START_HOUR}h
 * às {@value #NIGHT_END_HOUR}h; o fim-de-semana é o sábado e o domingo. Cada
 * fracção paga o valor da hora a que começa. As horas da noite e os dias do
 * fim-de-semana são fixos; os valores mudam-se no ecrã da tarifa.
 *
 * <p><b>Os valores são em centavos de metical, num {@code long}.</b>
 * 150,50 MZN guarda-se como {@code 15050}. Com {@code double}, somas como
 * 0,10 + 0,20 não dão exactamente 0,30, e um parque que cobra o dia todo
 * acumularia diferenças de arredondamento; com centavos inteiros as contas
 * são exactas. Só se converte para meticais ao mostrar, na interface. A
 * mesma convenção vale para {@link Payment}.
 *
 * <p>Esta classe só guarda os números. Calcular o valor de uma estadia é
 * trabalho de {@code service.BillingService}.
 *
 * <p>Imutável, excepto o identificador da base de dados: mudar a tarifa é
 * criar uma tarifa nova.
 */

public class Tariff {
    
    /**
     * Tolerância gratuita, em minutos (RN01). Uma estadia que não passe deste
     * valor, inclusive, não paga nada; passada a tolerância, paga-se a
     * estadia toda desde a entrada, sem descontar estes minutos. Zero quer
     * dizer sem tolerância. Nunca negativa.
     */
    private final int freeMinutes;

    /**
     * Fracção mínima de cobrança, em minutos (RN02). O tempo que sobra depois
     * dos dias completos arredonda-se para cima até um múltiplo deste valor:
     * com fracções de 15 min, uma estadia de 61 min cobra-se como 75. Entre 1
     * (cobrança ao minuto) e 60 (cobrança à hora).
     */
    private final int fractionMinutes;

    /**
     * Valor de uma hora de estacionamento, em centavos de metical: 60 MZN
     * guarda-se como {@code 6000}. Uma fracção paga a parte proporcional,
     * arredondada para cima ao centavo. Nunca negativo.
     */
    private final long hourlyRateCents;

    /**
     * Tecto diário, em centavos de metical (RN03): o máximo que se paga por
     * cada bloco de 24 h desde a entrada. Cada dia completo paga exactamente
     * este valor, e o resto do último dia nunca paga mais do que ele. Nunca
     * menor que {@link #hourlyRateCents}.
     */
    private final long dailyCapCents;

    /** Primeira hora da noite (inclusive). */
    public static final int NIGHT_START_HOUR = 20;

    /** Hora a que a noite acaba (exclusive): às 7h00 já é dia. */
    public static final int NIGHT_END_HOUR = 7;

    /**
     * Valor de uma hora de noite, em centavos. Igual ao de dia quer dizer
     * sem preço nocturno. Nunca negativo.
     */
    private final long nightRateCents;

    /**
     * Desconto ao sábado e ao domingo, em percentagem (0 a 100), sobre as
     * fracções que começam nesses dias. Zero quer dizer sem desconto.
     */
    private final int weekendDiscountPercent;

    /**
     * Multa por talão perdido, em centavos (RF17): soma-se ao valor da
     * estadia quando o carro sai sem talão. Nunca negativa.
     */
    private final long lostTicketCents;

    /**
     * Valor de uma avença mensal, em centavos (RF16): o que se paga por um
     * mês de estacionamento de uma matrícula. Nunca negativo.
     */
    private final long monthlyPassCents;

    private long id;

    /**
     * Uma tarifa sem preços por período, sem multa e sem avença: a noite
     * paga o mesmo que o dia, não há desconto ao fim-de-semana, e a multa e
     * a avença valem 0.
     */
    public Tariff(int freeMinutes, int fractionMinutes, long hourlyRateCents, long dailyCapCents) {
        this(freeMinutes, fractionMinutes, hourlyRateCents, dailyCapCents, hourlyRateCents, 0, 0, 0);
    }

    /**
     * A tarifa completa.
     *
     * @param freeMinutes a tolerância gratuita, em minutos
     * @param fractionMinutes a fracção mínima, de 1 a 60 minutos
     * @param hourlyRateCents o valor por hora de dia, em centavos
     * @param dailyCapCents o tecto diário, em centavos
     * @param nightRateCents o valor por hora de noite, em centavos
     * @param weekendDiscountPercent o desconto ao fim-de-semana, de 0 a 100
     * @param lostTicketCents a multa por talão perdido, em centavos
     * @param monthlyPassCents o valor da avença mensal, em centavos
     * @throws IllegalArgumentException se algum valor estiver fora do intervalo
     */
    public Tariff(int freeMinutes, int fractionMinutes, long hourlyRateCents, long dailyCapCents,
            long nightRateCents, int weekendDiscountPercent, long lostTicketCents, long monthlyPassCents) {
        if (freeMinutes < 0) {
            throw new IllegalArgumentException("freeMinutes não pode ser negativa: " + freeMinutes);
        }
        if (fractionMinutes < 1 || fractionMinutes > 60) {
            throw new IllegalArgumentException("fractionMinutes tem de estar entre 1 e 60: " + fractionMinutes);
        }
        if (hourlyRateCents < 0) {
            throw new IllegalArgumentException("hourlyRateCents  não pode ser negativa: " + hourlyRateCents);
        }
        if (dailyCapCents < hourlyRateCents) {
            throw new IllegalArgumentException("dailyCapCents tem que estar ao menos no intervalo em que para hourlyRateCents temos: "
                    + dailyCapCents + " < " + hourlyRateCents);
        }
        if (nightRateCents < 0) {
            throw new IllegalArgumentException("nightRateCents não pode ser negativo: " + nightRateCents);
        }
        if (weekendDiscountPercent < 0 || weekendDiscountPercent > 100) {
            throw new IllegalArgumentException("weekendDiscountPercent tem de estar entre 0 e 100: "
                    + weekendDiscountPercent);
        }
        if (lostTicketCents < 0) {
            throw new IllegalArgumentException("lostTicketCents não pode ser negativo: " + lostTicketCents);
        }
        if (monthlyPassCents < 0) {
            throw new IllegalArgumentException("monthlyPassCents não pode ser negativo: " + monthlyPassCents);
        }
        this.freeMinutes = freeMinutes;
        this.fractionMinutes = fractionMinutes;
        this.hourlyRateCents = hourlyRateCents;
        this.dailyCapCents = dailyCapCents;
        this.nightRateCents = nightRateCents;
        this.weekendDiscountPercent = weekendDiscountPercent;
        this.lostTicketCents = lostTicketCents;
        this.monthlyPassCents = monthlyPassCents;
    }

    public int getFreeMinutes() {
        return freeMinutes;
    }

    public int getFractionMinutes() {
        return fractionMinutes;
    }

    public long getHourlyRateCents() {
        return hourlyRateCents;
    }

    public long getDailyCapCents() {
        return dailyCapCents;
    }

    /** @return o valor por hora de noite, em centavos */
    public long getNightRateCents() {
        return nightRateCents;
    }

    /** @return o desconto ao fim-de-semana, em percentagem */
    public int getWeekendDiscountPercent() {
        return weekendDiscountPercent;
    }

    /** @return a multa por talão perdido, em centavos */
    public long getLostTicketCents() {
        return lostTicketCents;
    }

    /** @return o valor da avença mensal, em centavos */
    public long getMonthlyPassCents() {
        return monthlyPassCents;
    }

    public long getId() {
        return id;
    }

     public void setId(long newId) {
        if (newId <= 0) {
            throw new IllegalArgumentException("id  não pode ser negativa: " + newId);
        }
        if (id != 0) {
            throw new IllegalStateException("id já definida: " + id);
        }
        id = newId;
    }
}
