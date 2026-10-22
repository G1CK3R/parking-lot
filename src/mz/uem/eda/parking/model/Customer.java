package mz.uem.eda.parking.model;

import java.util.Objects;

/**
 * Um cliente registado: o condutor habitual, com nome e contacto. O condutor
 * ocasional não precisa de ser cliente; a viatura fica sem cliente
 * associado.
 *
 * <p><b>O cliente não guarda as suas matrículas.</b> Seria preciso uma lista
 * dentro do cliente, ou seja, uma colecção fora da restrição da cadeira. A
 * ligação faz-se no sentido contrário: cada {@link Vehicle} aponta para o
 * seu cliente, e as matrículas de um cliente obtêm-se percorrendo as
 * viaturas.
 *
 * <p>Imutável, excepto o identificador da base de dados.
 */
public class Customer {
    
    private final String name;
    private final String phone;
    private final boolean preferential;


    /* Identificador na base de dados; enquanto não for gravado. */
    private long id;

    public Customer(String name, String phone, boolean preferential){
        Objects.requireNonNull(name, "O name não pode ser nulo");
        if (name.isBlank()) {
            throw new IllegalArgumentException("O name não pode ser em branco");
        }
        this.name = name.strip();
        this.phone = phone;
        this.preferential = preferential;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isPreferential() {
        return preferential;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("id deve ser positive: " + id);
        }
        if (this.id != 0) {
            throw new IllegalStateException("id já definido: " + this.id);
        }
        this.id = id;
    }

    @Override 
    public String toString(){
        return name;
    }
}