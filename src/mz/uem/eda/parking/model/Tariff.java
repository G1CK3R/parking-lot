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

    /**
     * Cria uma tarifa.
     *
     * @param freeMinutes a tolerância gratuita em minutos (RN01, à volta de
     *        15): uma estadia até este tempo não paga; 0 ou mais
     * @param fractionMinutes a fracção mínima de cobrança em minutos (RN02):
     *        o tempo pago arredonda para cima a múltiplos desta fracção; de 1
     *        a 60
     * @param hourlyRateCents o valor por hora, em centavos; 0 ou mais
     * @param dailyCapCents o tecto diário (RN03), em centavos: nenhum dia de
     *        estadia custa mais do que isto; igual ou maior que
     *        {@code hourlyRateCents}
     * @throws IllegalArgumentException se algum valor sair dos limites acima
     */
    public Tariff(int freeMinutes, int fractionMinutes, long hourlyRateCents, long dailyCapCents) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a tolerância gratuita, em minutos */
    public int getFreeMinutes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a fracção mínima de cobrança, em minutos */
    public int getFractionMinutes() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o valor por hora, em centavos de metical */
    public long getHourlyRateCents() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o tecto diário, em centavos de metical */
    public long getDailyCapCents() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o identificador na base de dados, ou 0 se a tarifa ainda não
     *         foi gravada
     */
    public long getId() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Regista o identificador atribuído pela base de dados. Só a camada
     * {@code persistence} chama este método, uma vez.
     *
     * @param id o identificador, maior que 0
     * @throws IllegalArgumentException se {@code id} não for positivo
     * @throws IllegalStateException se o identificador já tiver sido
     *         atribuído
     */
    public void setId(long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
