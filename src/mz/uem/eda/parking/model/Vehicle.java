package mz.uem.eda.parking.model;

/**
 * Uma viatura, identificada pela matrícula. O condutor não acede ao sistema:
 * é a matrícula que o identifica (RF06).
 *
 * <p>A matrícula é guardada já <b>normalizada</b> (ver
 * {@link #normalizePlate(String)}), para que «aaa 123 mc», «AAA-123-MC» e
 * «AAA 123 MC» sejam a mesma viatura. A busca sequencial por matrícula
 * normaliza o texto introduzido pelo operador da mesma forma antes de
 * comparar.
 *
 * <p>Imutável, excepto o identificador da base de dados.
 */
public class Vehicle {

    /**
     * Tipo de viatura registado à entrada (RF06). Lista provisória, a
     * confirmar com o grupo; no MVP não influencia a colocação nem a tarifa.
     */
    public enum Type {
        CAR,
        MOTORCYCLE,
        VAN
    }

    /**
     * Cria uma viatura.
     *
     * @param plate a matrícula, em qualquer formato; é normalizada aqui
     * @param type o tipo de viatura, nunca {@code null}
     * @param customer o cliente associado, ou {@code null} se o condutor for
     *        ocasional
     * @throws NullPointerException se {@code plate} ou {@code type} for
     *         {@code null}
     * @throws IllegalArgumentException se a matrícula ficar vazia depois de
     *         normalizada
     */
    public Vehicle(String plate, Type type, Customer customer) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Põe uma matrícula na forma canónica: maiúsculas, sem espaços nem
     * hífenes. «aaa 123 mc» e «AAA-123-MC» ficam ambas «AAA123MC».
     *
     * <p>Não valida o formato: há matrículas de vários formatos em
     * circulação e o sistema não tem de as conhecer. Para mostrar ao
     * operador, a interface pode voltar a inserir os espaços.
     *
     * @param plate o texto introduzido, nunca {@code null}
     * @return a matrícula normalizada; pode ser vazia se {@code plate} só
     *         tiver espaços e hífenes
     * @throws NullPointerException se {@code plate} for {@code null}
     * @complexity O(m), m = comprimento do texto
     */
    public static String normalizePlate(String plate) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return a matrícula normalizada, nunca {@code null} nem vazia
     */
    public String getPlate() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o tipo de viatura, nunca {@code null}
     */
    public Type getType() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o cliente associado, ou {@code null} se o condutor for ocasional
     */
    public Customer getCustomer() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o identificador na base de dados, ou 0 se a viatura ainda não
     *         foi gravada
     */
    public long getId() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Regista o identificador atribuído pela base de dados. Só a camada
     * {@code persistence} chama este método, uma vez, ao gravar ou ao repor.
     *
     * @param id o identificador, maior que 0
     * @throws IllegalArgumentException se {@code id} não for positivo
     * @throws IllegalStateException se o identificador já tiver sido
     *         atribuído
     */
    public void setId(long id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Duas viaturas são iguais se tiverem a mesma matrícula normalizada. O
     * tipo e o cliente não contam.
     */
    @Override
    public boolean equals(Object other) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Coerente com {@link #equals(Object)}: calculado só a partir da matrícula. */
    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a matrícula normalizada, para depuração e para a consola */
    @Override
    public String toString() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
