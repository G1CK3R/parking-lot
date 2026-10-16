package mz.uem.eda.parking.model;

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

    /**
     * Cria um cliente.
     *
     * @param name o nome, nunca {@code null} nem vazio
     * @param phone o contacto telefónico, ou {@code null} se não for dado
     * @param usualStayClass a classe de permanência habitual (um cliente de
     *        avença é {@link StayClass#LONG_TERM}), ou {@code null} se não
     *        houver uma habitual
     * @param preferential {@code true} se o cliente tiver direito à fila de
     *        espera preferencial
     * @throws NullPointerException se {@code name} for {@code null}
     * @throws IllegalArgumentException se {@code name} estiver vazio
     */
    public Customer(String name, String phone, StayClass usualStayClass, boolean preferential) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o nome, nunca {@code null} nem vazio */
    public String getName() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o contacto telefónico, ou {@code null} se não tiver sido dado */
    public String getPhone() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return a classe de permanência habitual, ou {@code null} se não houver
     *         uma. Serve de sugestão à entrada; não substitui a duração
     *         estimada da estadia.
     */
    public StayClass getUsualStayClass() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return {@code true} se, com o parque cheio, o cliente entrar na fila
     *         de espera preferencial em vez da normal (ADR-007)
     */
    public boolean isPreferential() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o identificador na base de dados, ou 0 se o cliente ainda não
     *         foi gravado
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

    /** @return o nome, para depuração e para a consola */
    @Override
    public String toString() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
