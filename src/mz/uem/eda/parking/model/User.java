package mz.uem.eda.parking.model;

import java.util.Objects;

public class User {
    public enum Role {
        OPERATOR, 
        
        ADMIN
    }
    
    private final String username;
    private final Role role;
    private String passwordHash;
    private String salt;
    private int iterations;

    private long id;

    public User(String username, String passwordHash, String salt, int iterations, Role role) {
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(role, "role must not be null");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        this.username = username;
        this.role = role;
        setCredentials(passwordHash, salt, iterations);
    }

     /** @return o nome de utilizador, nunca {@code null} nem vazio */
    public String getUsername() {
        return username;
    }

    /** @return o hash PBKDF2 em Base64 */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** @return o sal em Base64 */
    public String getSalt() {
        return salt;
    }

    /** @return o número de iterações usado no hash */
    public int getIterations() {
        return iterations;
    }

    /** @return o perfil, nunca {@code null} */
    public Role getRole() {
        return role;
    }

    public void changePassword(String passwordHash, String salt, int iterations) {
        setCredentials(passwordHash, salt, iterations);
    }

    /**
     * Valida e guarda as três peças. Partilhado pelo construtor e por
     * {@link #changePassword}, para que as regras fiquem num só sítio.
     */
    private void setCredentials(String passwordHash, String salt, int iterations) {
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        Objects.requireNonNull(salt, "salt must not be null");
        if (passwordHash.isBlank() || salt.isBlank()) {
            throw new IllegalArgumentException("passwordHash and salt must not be blank");
        }
        if (iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive: " + iterations);
        }
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.iterations = iterations;
    }

    /**
     * @return o identificador na base de dados, ou 0 se o utilizador ainda
     *         não foi gravado
     */
    public long getId() {
        return id;
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
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive: " + id);
        }
        if (this.id != 0) {
            throw new IllegalStateException("id already set: " + this.id);
        }
        this.id = id;
    }

    /** Dois utilizadores são iguais se tiverem o mesmo nome de utilizador. */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User)) {
            return false;
        }
        return username.equals(((User) other).username);
    }

    /** Coerente com {@link #equals(Object)}: calculado só a partir do nome de utilizador. */
    @Override
    public int hashCode() {
        return username.hashCode();
    }

    /**
     * @return o nome de utilizador e o perfil, por exemplo
     *         {@code "ana (OPERATOR)"}; <b>nunca</b> o hash nem o sal
     */
    @Override
    public String toString() {
        return username + " (" + role + ")";
    }

}