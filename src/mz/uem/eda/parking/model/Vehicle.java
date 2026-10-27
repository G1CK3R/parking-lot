package mz.uem.eda.parking.model;

import java.util.Objects;

/**
 * Uma viatura: a matrícula normalizada e o tipo (ligeiro, motociclo,
 * carrinha). O tipo escolhe-se à entrada e aparece no talão, no recibo, na
 * pesquisa, no histórico e na dica do rato; não muda a baia nem o preço.
 *
 * <p>Não há cliente associado: o parque trabalha com condutores
 * ocasionais, e quem precisa de ser identificado (uma avença) guarda o nome
 * e o telefone na própria {@link MonthlyPass}. A marca de preferencial é do
 * balcão, à entrada, e vai directamente para a fila de espera.
 *
 * <p>Imutável, excepto o identificador da base de dados.
 */
public class Vehicle {

    /** O tipo de viatura. */
    public enum Type {
        CAR,
        MOTORCYCLE,
        VAN
    }

    private final String plate;
    private final Type type;

    /** Identificador na base de dados */
    private long id;


    /**
     * @param plate a matrícula, com ou sem espaços e hífenes
     * @param type o tipo de viatura
     * @throws IllegalArgumentException se a matrícula ficar vazia
     */
    public Vehicle(String plate, Type type) {
        Objects.requireNonNull(type, "type must not be null");
        String normalized =  normalizePlate(plate);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Plate não pode estár vazio: ' " + plate);
        }       
        this.plate =  normalized;
        this.type =  type;
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
    public static String normalizePlate(String plate){
        Objects.requireNonNull(plate, "plate não pode ser null");
        StringBuilder result = new StringBuilder(plate.length());
        for(int i = 0; i < plate.length(); i++) {
            char c =  plate.charAt(i);
            if (c != ' ' &&  c != '-') {
                result.append(Character.toUpperCase(c));
            }
        }
        return result.toString();
    }

    public String getPlate() {
        return plate;
    }

    public Type getType() {
        return type;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        if(id < 0) {
            throw new IllegalArgumentException("id deve ser positivo: " + id);
        }
        if (this.id != 0) {
            throw new IllegalStateException("id já definido: " + this.id);
        }
        this.id = id;
    }

    /**
     * Duas viaturas são iguais se tiverem a mesma matrícula normalizada. O
     * tipo não conta.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vehicle)) {
            return false;
        }
        return plate.equals(((Vehicle) other).plate);
    }

    /** @return a matrícula normalizada, para depuração e para a consola */
    @Override
    public String toString() {
        return plate;
    }
}