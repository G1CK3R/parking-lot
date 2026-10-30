package mz.uem.eda.parking.service.access;

import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.persistence.security.PasswordHasher;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * Autenticação e gestão de utilizadores (RF01, RF02, RF03, RF22).
 *
 * <p>Os utilizadores ficam numa {@link LinkedQueue}, percorrida só para
 * leitura para encontrar um nome (busca sequencial, como no resto do
 * projecto). Com poucos utilizadores, O(u) não pesa. A {@code persistence}
 * carrega-os no arranque com {@link #load}.
 *
 * <p>As palavras-passe nunca se guardam: só o hash PBKDF2, o sal e as
 * iterações (ver {@link PasswordHasher}). Chegam como {@code char[]}, e quem
 * chama apaga-as depois de usar ({@code java.util.Arrays.fill(pw, '\0')}).
 *
 * <p>As operações de administração recebem o utilizador que as pede e
 * recusam-nas se ele não for administrador (RF02). A interface também
 * esconde esses menus, mas a regra vive aqui.
 *
 * <p><b>Bloqueio (RF03).</b> {@value #MAX_FAILED_ATTEMPTS} palavras-passe
 * erradas seguidas bloqueiam a conta durante {@value #LOCK_MINUTES} minutos;
 * durante o bloqueio nem a palavra-passe certa entra. Um login certo esquece
 * as tentativas. O administrador pode desbloquear antes, e repor a
 * palavra-passe também desbloqueia. Como o bloqueio acaba sozinho, o último
 * administrador nunca fica trancado para sempre.
 */
public class AuthService {

    /** Comprimento mínimo de uma palavra-passe nova. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** Quantas palavras-passe erradas seguidas bloqueiam a conta. */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** Quantos minutos dura o bloqueio. */
    public static final int LOCK_MINUTES = 15;

    private final PasswordHasher hasher;
    private final Queue<User> users;

    /**
     * Hash de uma palavra-passe qualquer, usado quando o nome não existe,
     * para o login demorar o mesmo e não revelar que nomes existem.
     */
    private final String dummySalt;
    private final String dummyHash;

    /**
     * Cria o serviço sem utilizadores.
     *
     * @param hasher o hasher das palavras-passe
     * @throws NullPointerException se {@code hasher} for {@code null}
     */
    public AuthService(PasswordHasher hasher) {
        this.hasher = Objects.requireNonNull(hasher, "hasher não pode ser null");
        this.users = new LinkedQueue<>();
        this.dummySalt = hasher.newSalt();
        this.dummyHash = hasher.hash("dummy-password".toCharArray(), dummySalt, hasher.getIterations());
    }

    /**
     * Acrescenta um utilizador já gravado, com o hash que veio da base de
     * dados. Só a {@code persistence} chama este método, no arranque.
     *
     * @param user o utilizador gravado
     * @throws IllegalStateException se já houver um utilizador com o mesmo
     *         nome
     * @complexity O(u), u = utilizadores carregados
     */
    public void load(User user) {
        Objects.requireNonNull(user, "user não pode ser null");
        if (find(user.getUsername()) != null) {
            throw new IllegalStateException("utilizador repetido: " + user.getUsername());
        }
        users.enqueue(user);
    }

    /**
     * Cria o primeiro administrador, quando ainda não há nenhum utilizador.
     * É o único registo que não pede um administrador.
     *
     * @param username o nome do administrador
     * @param password a palavra-passe
     * @return o administrador criado
     * @throws IllegalStateException se já houver utilizadores
     * @throws IllegalArgumentException se a palavra-passe for curta demais
     * @complexity O(1), mais o custo fixo do PBKDF2
     */
    public User createFirstAdmin(String username, char[] password) {
        if (!users.isEmpty()) {
            throw new IllegalStateException("já existem utilizadores");
        }
        return add(username, password, User.Role.ADMIN);
    }

    /**
     * Verifica o nome e a palavra-passe (RF01).
     *
     * @param username o nome escrito
     * @param password a palavra-passe escrita
     * @return o utilizador autenticado, ou {@code null} se o nome não existir
     *         ou a palavra-passe estiver errada: a interface mostra a mesma
     *         mensagem nos dois casos
     * @complexity O(u) para encontrar o nome, mais o custo fixo do PBKDF2
     */
    public User login(String username, char[] password) {
        return login(username, password, LocalDateTime.now());
    }

    /**
     * Verifica o nome e a palavra-passe, com o bloqueio por tentativas
     * erradas (RF01, RF03).
     *
     * @param username o nome escrito
     * @param password a palavra-passe escrita
     * @param now a hora da tentativa
     * @return o utilizador autenticado, ou {@code null} se o nome não
     *         existir, se a palavra-passe estiver errada ou se a conta
     *         estiver bloqueada
     * @complexity O(u) para encontrar o nome, mais o custo fixo do PBKDF2
     */
    public User login(String username, char[] password, LocalDateTime now) {
        Objects.requireNonNull(username, "username não pode ser null");
        Objects.requireNonNull(password, "password não pode ser null");
        Objects.requireNonNull(now, "now não pode ser null");
        User user = find(username);
        if (user == null || user.isLockedAt(now)) {
            hasher.matches(password, dummyHash, dummySalt, hasher.getIterations());
            return null;
        }
        boolean ok = hasher.matches(password, user.getPasswordHash(), user.getSalt(),
                user.getIterations());
        if (ok) {
            user.unlock();
            return user;
        }
        user.registerFailure(now, MAX_FAILED_ATTEMPTS, LOCK_MINUTES);
        return null;
    }

    /**
     * Desbloqueia uma conta antes de o bloqueio acabar. Só um administrador
     * pode.
     *
     * @param admin quem pede
     * @param username o utilizador a desbloquear
     * @return {@code true} se desbloqueou, {@code false} se o nome não existir
     * @throws SecurityException se {@code admin} não for administrador
     * @complexity O(u)
     */
    public boolean unlock(User admin, String username) {
        requireAdmin(admin);
        User user = find(username);
        if (user == null) {
            return false;
        }
        user.unlock();
        return true;
    }

    /**
     * Cria um utilizador (RF22). Só um administrador pode.
     *
     * @param admin quem pede
     * @param username o nome do novo utilizador
     * @param password a palavra-passe inicial
     * @param role o perfil
     * @return o utilizador criado
     * @throws SecurityException se {@code admin} não for administrador
     * @throws IllegalStateException se o nome já existir
     * @throws IllegalArgumentException se a palavra-passe for curta demais
     * @complexity O(u) mais o PBKDF2
     */
    public User register(User admin, String username, char[] password, User.Role role) {
        requireAdmin(admin);
        return add(username, password, role);
    }

    /**
     * Muda a palavra-passe do próprio utilizador (RF03), pedindo a actual.
     *
     * @param user o utilizador autenticado
     * @param current a palavra-passe actual
     * @param replacement a palavra-passe nova
     * @return {@code true} se mudou, {@code false} se a actual estiver
     *         errada
     * @throws IllegalArgumentException se a nova for curta demais
     * @complexity O(1), mais o custo fixo do PBKDF2 (verificar a actual e calcular a
     *             nova)
     */
    public boolean changePassword(User user, char[] current, char[] replacement) {
        Objects.requireNonNull(user, "user não pode ser null");
        if (!hasher.matches(current, user.getPasswordHash(), user.getSalt(), user.getIterations())) {
            return false;
        }
        setPassword(user, replacement);
        return true;
    }

    /**
     * Define uma palavra-passe nova a outro utilizador, por exemplo se ele a
     * esqueceu (RF22). Só um administrador pode, e não precisa da antiga. Se
     * a conta estava bloqueada, fica desbloqueada.
     *
     * @param admin quem pede
     * @param username o utilizador a alterar
     * @param replacement a palavra-passe nova
     * @return {@code true} se mudou, {@code false} se o nome não existir
     * @throws SecurityException se {@code admin} não for administrador
     * @throws IllegalArgumentException se a nova for curta demais
     * @complexity O(u), mais o custo fixo do PBKDF2
     */
    public boolean resetPassword(User admin, String username, char[] replacement) {
        requireAdmin(admin);
        User user = find(username);
        if (user == null) {
            return false;
        }
        setPassword(user, replacement);
        user.unlock();
        return true;
    }

    /**
     * Apaga um utilizador (RF22). Só um administrador pode, nunca a si
     * próprio, e nunca o último administrador.
     *
     * <p>Tirar um elemento do meio de uma fila faz-se só com operações de
     * fila: dá-se uma volta completa, desenfileirando cada utilizador e
     * voltando a enfileirar todos menos o apagado. A ordem dos outros fica
     * igual.
     *
     * @param admin quem pede
     * @param username o utilizador a apagar
     * @return {@code true} se apagou, {@code false} se o nome não existir
     * @throws SecurityException se {@code admin} não for administrador
     * @throws IllegalStateException se for o próprio ou o último
     *         administrador
     * @complexity O(u): uma volta à fila
     */
    public boolean remove(User admin, String username) {
        requireAdmin(admin);
        User target = find(username);
        if (target == null) {
            return false;
        }
        if (target.equals(admin)) {
            throw new IllegalStateException("um administrador não se pode apagar a si próprio");
        }
        if (target.getRole() == User.Role.ADMIN && countAdmins() == 1) {
            throw new IllegalStateException("não se pode apagar o último administrador");
        }
        int size = users.size();
        for (int i = 0; i < size; i++) {
            User user = users.dequeue();
            if (!user.equals(target)) {
                users.enqueue(user);
            }
        }
        return true;
    }

    /**
     * @param user o utilizador, ou {@code null}
     * @return {@code true} se for administrador
     * @complexity O(1)
     */
    public static boolean isAdmin(User user) {
        return user != null && user.getRole() == User.Role.ADMIN;
    }

    /**
     * Recusa a operação se o utilizador não for administrador (RF02).
     *
     * @param user quem pede
     * @throws SecurityException se não for administrador
     * @complexity O(1)
     */
    public static void requireAdmin(User user) {
        if (!isAdmin(user)) {
            throw new SecurityException("operação só para administradores");
        }
    }

    /**
     * Procura o utilizador pelo nome, com uma busca sequencial só de leitura.
     *
     * @param username o nome
     * @return o utilizador, ou {@code null} se não existir
     * @complexity O(u)
     */
    public User find(String username) {
        Objects.requireNonNull(username, "username não pode ser null");
        for (User user : users) {
            if (user.getUsername().equals(username)) {
                return user;
            }
        }
        return null;
    }

    /**
     * @return quantos utilizadores existem; O(1)
     * @complexity O(1)
     */
    public int size() {
        return users.size();
    }

    /**
     * Percurso só de leitura dos utilizadores, pela ordem de criação. Para o
     * ecrã de administração e para a {@code persistence} gravar.
     *
     * @return os utilizadores
     * @complexity O(1) para obter; O(u) para percorrer
     */
    public Iterable<User> users() {
        return users;
    }

    private User add(String username, char[] password, User.Role role) {
        Objects.requireNonNull(username, "username não pode ser null");
        if (find(username) != null) {
            throw new IllegalStateException("já existe o utilizador: " + username);
        }
        checkStrength(password);
        String salt = hasher.newSalt();
        int iterations = hasher.getIterations();
        User user = new User(username, hasher.hash(password, salt, iterations), salt, iterations, role);
        users.enqueue(user);
        return user;
    }

    private void setPassword(User user, char[] replacement) {
        checkStrength(replacement);
        String salt = hasher.newSalt();
        int iterations = hasher.getIterations();
        user.changePassword(hasher.hash(replacement, salt, iterations), salt, iterations);
    }

    private static void checkStrength(char[] password) {
        Objects.requireNonNull(password, "password não pode ser null");
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("a palavra-passe tem de ter pelo menos "
                    + MIN_PASSWORD_LENGTH + " caracteres");
        }
    }

    private int countAdmins() {
        int count = 0;
        for (User user : users) {
            if (user.getRole() == User.Role.ADMIN) {
                count++;
            }
        }
        return count;
    }
}