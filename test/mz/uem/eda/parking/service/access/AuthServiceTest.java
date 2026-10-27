package mz.uem.eda.parking.service.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.persistence.security.PasswordHasher;

class AuthServiceTest {

    /** Poucas iterações, só para os testes correrem depressa. */
    private final PasswordHasher hasher = new PasswordHasher(1000);

    private AuthService auth;
    private User admin;

    @BeforeEach
    void setUp() {
        auth = new AuthService(hasher);
        admin = auth.createFirstAdmin("admin", pw("admin-pass"));
    }

    // ---- login ----------------------------------------------------------

    @Test
    void loginWithTheRightPassword() {
        assertSame(admin, auth.login("admin", pw("admin-pass")));
    }

    @Test
    void loginFailsWithWrongPasswordOrUnknownName() {
        assertNull(auth.login("admin", pw("wrong-pass")));
        assertNull(auth.login("nobody", pw("admin-pass")));
        assertNull(auth.login("ADMIN", pw("admin-pass")));
    }

    // ---- bloqueio por tentativas (RF03) ------------------------------------

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 20, 8, 0);

    @Test
    void fiveWrongPasswordsInARowLockTheAccount() {
        for (int i = 1; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            assertNull(auth.login("admin", pw("wrong-pass"), T0));
            assertEquals(i, admin.getFailedAttempts());
            assertFalse(admin.isLockedAt(T0));
        }
        assertNull(auth.login("admin", pw("wrong-pass"), T0));
        assertTrue(admin.isLockedAt(T0));
        assertEquals(T0.plusMinutes(AuthService.LOCK_MINUTES), admin.getLockedUntil());

        // Bloqueada, nem a palavra-passe certa entra.
        assertNull(auth.login("admin", pw("admin-pass"), T0.plusMinutes(14)));
        // Passados os 15 minutos, entra, e as tentativas voltam a zero.
        assertSame(admin, auth.login("admin", pw("admin-pass"), T0.plusMinutes(15)));
        assertEquals(0, admin.getFailedAttempts());
        assertNull(admin.getLockedUntil());
    }

    @Test
    void theRightPasswordResetsTheCount() {
        auth.login("admin", pw("wrong-pass"), T0);
        auth.login("admin", pw("wrong-pass"), T0);
        assertSame(admin, auth.login("admin", pw("admin-pass"), T0));
        assertEquals(0, admin.getFailedAttempts());
    }

    @Test
    void anAdminCanUnlockAndAResetAlsoUnlocks() {
        User ana = auth.register(admin, "ana", pw("operadora-1"), User.Role.OPERATOR);
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            auth.login("ana", pw("wrong-pass"), T0);
        }
        assertTrue(ana.isLockedAt(T0));

        assertThrows(SecurityException.class, () -> auth.unlock(ana, "ana"));
        assertTrue(auth.unlock(admin, "ana"));
        assertFalse(ana.isLockedAt(T0));
        assertFalse(auth.unlock(admin, "nobody"));

        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            auth.login("ana", pw("wrong-pass"), T0);
        }
        assertTrue(ana.isLockedAt(T0));
        assertTrue(auth.resetPassword(admin, "ana", pw("operadora-2")));
        assertSame(ana, auth.login("ana", pw("operadora-2"), T0));
    }

    @Test
    void neverStoresThePassword() {
        assertNotEquals("admin-pass", admin.getPasswordHash());
        assertFalse(admin.toString().contains(admin.getPasswordHash()));
    }

    // ---- primeiro administrador ----------------------------------------

    @Test
    void firstAdminOnlyOnce() {
        assertThrows(IllegalStateException.class, () -> auth.createFirstAdmin("other", pw("other-pass")));
    }

    // ---- registo --------------------------------------------------------

    @Test
    void adminRegistersOperators() {
        User op = auth.register(admin, "ana", pw("ana-pass1"), User.Role.OPERATOR);

        assertEquals(User.Role.OPERATOR, op.getRole());
        assertSame(op, auth.login("ana", pw("ana-pass1")));
        assertEquals(2, auth.size());
    }

    @Test
    void operatorCannotRegister() {
        User op = auth.register(admin, "ana", pw("ana-pass1"), User.Role.OPERATOR);
        assertThrows(SecurityException.class,
                () -> auth.register(op, "rui", pw("rui-pass1"), User.Role.OPERATOR));
        assertThrows(SecurityException.class,
                () -> auth.register(null, "rui", pw("rui-pass1"), User.Role.OPERATOR));
    }

    @Test
    void rejectsRepeatedNamesAndShortPasswords() {
        assertThrows(IllegalStateException.class,
                () -> auth.register(admin, "admin", pw("another-pass"), User.Role.OPERATOR));
        assertThrows(IllegalArgumentException.class,
                () -> auth.register(admin, "ana", pw("short"), User.Role.OPERATOR));
    }

    @Test
    void samePasswordGivesDifferentHashes() {
        User a = auth.register(admin, "ana", pw("same-pass"), User.Role.OPERATOR);
        User b = auth.register(admin, "rui", pw("same-pass"), User.Role.OPERATOR);
        assertNotEquals(a.getSalt(), b.getSalt());
        assertNotEquals(a.getPasswordHash(), b.getPasswordHash());
    }

    // ---- palavras-passe -------------------------------------------------

    @Test
    void changePasswordNeedsTheCurrentOne() {
        assertFalse(auth.changePassword(admin, pw("wrong-pass"), pw("new-admin-pass")));
        assertTrue(auth.changePassword(admin, pw("admin-pass"), pw("new-admin-pass")));

        assertNull(auth.login("admin", pw("admin-pass")));
        assertSame(admin, auth.login("admin", pw("new-admin-pass")));
    }

    @Test
    void adminResetsAForgottenPassword() {
        auth.register(admin, "ana", pw("ana-pass1"), User.Role.OPERATOR);

        assertTrue(auth.resetPassword(admin, "ana", pw("ana-pass2")));
        assertFalse(auth.resetPassword(admin, "nobody", pw("ana-pass2")));
        assertTrue(auth.login("ana", pw("ana-pass2")) != null);
    }

    // ---- apagar ---------------------------------------------------------

    @Test
    void removeKeepsTheOthersInOrder() {
        auth.register(admin, "ana", pw("ana-pass1"), User.Role.OPERATOR);
        auth.register(admin, "rui", pw("rui-pass1"), User.Role.OPERATOR);
        auth.register(admin, "eva", pw("eva-pass1"), User.Role.OPERATOR);

        assertTrue(auth.remove(admin, "rui"));
        assertFalse(auth.remove(admin, "rui"));

        StringBuilder names = new StringBuilder();
        for (User user : auth.users()) {
            names.append(user.getUsername()).append(' ');
        }
        assertEquals("admin ana eva ", names.toString());
        assertNull(auth.login("rui", pw("rui-pass1")));
    }

    @Test
    void cannotRemoveYourselfOrTheLastAdmin() {
        assertThrows(IllegalStateException.class, () -> auth.remove(admin, "admin"));

        User second = auth.register(admin, "chefe", pw("chefe-pass"), User.Role.ADMIN);
        assertTrue(auth.remove(second, "admin"));
        assertThrows(IllegalStateException.class, () -> auth.remove(second, "chefe"));
    }

    // ---- carregar da base de dados --------------------------------------

    @Test
    void loadAcceptsStoredHashes() {
        AuthService fresh = new AuthService(hasher);
        String salt = hasher.newSalt();
        User stored = new User("ana", hasher.hash(pw("ana-pass1"), salt, 500), salt, 500, User.Role.OPERATOR);
        fresh.load(stored);

        // verificado com as 500 iterações guardadas, não com as 1000 actuais
        assertSame(stored, fresh.login("ana", pw("ana-pass1")));
        assertThrows(IllegalStateException.class, () -> fresh.load(stored));
    }

    // ---- PasswordHasher -------------------------------------------------

    @Test
    void hasherIsDeterministicForTheSameSalt() {
        String salt = hasher.newSalt();
        assertEquals(hasher.hash(pw("x-pass"), salt, 1000), hasher.hash(pw("x-pass"), salt, 1000));
        assertNotEquals(hasher.hash(pw("x-pass"), salt, 1000), hasher.hash(pw("x-pass"), salt, 1001));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(0));
    }

    private static char[] pw(String text) {
        return text.toCharArray();
    }
}
