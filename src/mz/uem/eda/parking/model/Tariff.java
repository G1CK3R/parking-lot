package mz.uem.eda.parking.model;


/**
 * A tabela de preços em vigor (RF13): tolerância gratuita, fracção mínima de
 * cobrança, valor por hora e tecto diário.
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
    
    private final int freeMinutes;
    private final int fractionMinutes;
    private final long hourlyRateCents;
    private final long dailyCapCents;

    private long id;

    public Tariff(int freeMinutes, int fractionMinutes, long hourlyRateCents, long dailyCapCents) {
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
        this.freeMinutes = freeMinutes;
        this.fractionMinutes = fractionMinutes;
        this.hourlyRateCents = hourlyRateCents;
        this.dailyCapCents = dailyCapCents;
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
