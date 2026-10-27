package mz.uem.eda.parking.model;

import java.time.LocalDateTime;
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

    /** Palavras-passe erradas seguidas desde o último login certo (RF03). */
    private int failedAttempts;

    /** Até quando a conta está bloqueada, ou {@code null} se não estiver. */
    private LocalDateTime lockedUntil;

    //A id serve como indentificador da base de dados
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

    /** @return quantas palavras-passe erradas seguidas houve desde o último login certo */
    public int getFailedAttempts() {
        return failedAttempts;
    }

    /** @return até quando a conta está bloqueada, ou {@code null} se nunca foi bloqueada */
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    /**
     * @param now a hora actual
     * @return {@code true} se a conta estiver bloqueada a esta hora
     */
    public boolean isLockedAt(LocalDateTime now) {
        Objects.requireNonNull(now, "now não pode ser null");
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Conta uma palavra-passe errada. Ao chegar a {@code maxAttempts}
     * seguidas, a conta fica bloqueada durante {@code lockMinutes} e a
     * contagem recomeça.
     *
     * @param now a hora da tentativa
     * @param maxAttempts quantas tentativas erradas seguidas bloqueiam
     * @param lockMinutes quantos minutos dura o bloqueio
     * @return {@code true} se esta tentativa bloqueou a conta
     */
    public boolean registerFailure(LocalDateTime now, int maxAttempts, int lockMinutes) {
        Objects.requireNonNull(now, "now não pode ser null");
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            failedAttempts = 0;
            lockedUntil = now.plusMinutes(lockMinutes);
            return true;
        }
        return false;
    }

    /** Desbloqueia a conta e esquece as tentativas erradas (login certo ou administrador). */
    public void unlock() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    /**
     * Repõe o estado do bloqueio lido da base de dados. Só a camada
     * {@code persistence} chama este método.
     *
     * @param attempts as tentativas erradas seguidas, 0 ou mais
     * @param until até quando está bloqueada, ou {@code null}
     */
    public void restoreLock(int attempts, LocalDateTime until) {
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts não pode ser negativo: " + attempts);
        }
        this.failedAttempts = attempts;
        this.lockedUntil = until;
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

    /**
     * @return o nome de utilizador e o perfil, por exemplo
     *         {@code "ana (OPERATOR)"}; <b>nunca</b> o hash nem o sal
     */
    @Override
    public String toString() {
        return username + " (" + role + ")";
    }

}