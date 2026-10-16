package mz.uem.eda.parking.model;

/**
 * Quem acede ao sistema: um operador ao balcão ou um administrador (RF01,
 * RF02). O condutor nunca é um {@code User}.
 *
 * <p>Guarda as três peças do PBKDF2WithHmacSHA256 (ADR-015) em separado,
 * como estão nas colunas da tabela {@code users}: o hash, o sal e o número
 * de iterações. Hash e sal vêm em Base64, prontos a gravar como texto.
 * Gerar e verificar o hash é trabalho de
 * {@code persistence.security.PasswordHasher}; esta classe só os transporta.
 *
 * <p><b>A palavra-passe em claro nunca passa por aqui.</b> E o
 * {@link #toString()} não mostra o hash nem o sal, para que não acabem num
 * registo da consola ou num relatório de erro.
 */
public class User {

    /** Perfil do utilizador; decide que ecrãs ficam disponíveis. */
    public enum Role {
        /** Regista entradas e saídas, cobra e emite talão. */
        OPERATOR,
        /** Gere tarifas, baias e utilizadores e consulta relatórios. */
        ADMIN
    }

    /**
     * Cria um utilizador.
     *
     * @param username o nome de utilizador, nunca {@code null} nem vazio;
     *        guardado tal como é dado (distingue maiúsculas)
     * @param passwordHash o hash PBKDF2 em Base64, nunca {@code null} nem vazio
     * @param salt o sal em Base64, nunca {@code null} nem vazio
     * @param iterations o número de iterações usado no hash, maior que 0
     * @param role o perfil, nunca {@code null}
     * @throws NullPointerException se algum argumento de objecto for
     *         {@code null}
     * @throws IllegalArgumentException se algum texto estiver vazio ou
     *         {@code iterations} não for positivo
     */
    public User(String username, String passwordHash, String salt, int iterations, Role role) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o nome de utilizador, nunca {@code null} nem vazio */
    public String getUsername() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o hash PBKDF2 em Base64 */
    public String getPasswordHash() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o sal em Base64 */
    public String getSalt() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o número de iterações usado no hash */
    public int getIterations() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o perfil, nunca {@code null} */
    public Role getRole() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Substitui as três peças da palavra-passe de uma vez, para a alteração
     * de palavra-passe (RF03). Trocar só uma delas deixaria o hash
     * impossível de verificar, por isso não há um modificador para cada.
     *
     * @param passwordHash o hash novo em Base64, nunca {@code null} nem vazio
     * @param salt o sal novo em Base64, nunca {@code null} nem vazio
     * @param iterations o número de iterações, maior que 0
     * @throws NullPointerException se {@code passwordHash} ou {@code salt}
     *         for {@code null}
     * @throws IllegalArgumentException se algum texto estiver vazio ou
     *         {@code iterations} não for positivo
     */
    public void changePassword(String passwordHash, String salt, int iterations) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o identificador na base de dados, ou 0 se o utilizador ainda
     *         não foi gravado
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

    /** Dois utilizadores são iguais se tiverem o mesmo nome de utilizador. */
    @Override
    public boolean equals(Object other) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Coerente com {@link #equals(Object)}: calculado só a partir do nome de utilizador. */
    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o nome de utilizador e o perfil, por exemplo
     *         {@code "ana (OPERATOR)"}; <b>nunca</b> o hash nem o sal
     */
    @Override
    public String toString() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
