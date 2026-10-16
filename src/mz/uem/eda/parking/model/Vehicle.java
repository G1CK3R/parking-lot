package mz.uem.eda.parking.model;

import java.util.Objects;

public class Vehicle {
    public enum Type {
        CAR,
        MOTORCYCLE,
        VAN
    }

    private final String plate;
    private final Type type;
    private final Customer customer;

    /** Identificador na base de dados */
    private long id;


    public Vehicle(String plate, Type type, Customer customer) {
        Objects.requireNonNull(type, "type must not be null");
        String normalized =  normalizePlate(plate);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Plate não pode estár vazio: ' " + plate);
        }       
        this.plate =  normalized;
        this.type =  type;
        this.customer =  customer;
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

    public Customer getCustomer() {
        return customer;
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
     * tipo e o cliente não contam.
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

    /** Coerente com {@link #equals(Object)}: calculado só a partir da matrícula. */
    @Override
    public int hashCode() {
        return plate.hashCode();
    }

    /** @return a matrícula normalizada, para depuração e para a consola */
    @Override
    public String toString() {
        return plate;
    }
}