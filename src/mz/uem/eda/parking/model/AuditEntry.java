package mz.uem.eda.parking.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Uma linha do registo de auditoria (RF23): quem fez uma operação sensível,
 * qual, e quando.
 *
 * <p>Os pormenores guardam-se como valores separados por {@value #SEPARATOR}
 * (por exemplo a matrícula e o talão), sem frases: é a interface que os
 * escreve em português, a partir de {@code strings_pt.properties}.
 *
 * <p>Imutável, excepto o identificador da base de dados.
 */
public class AuditEntry {

    /** O separador dos valores em {@link #getDetail()}. */
    public static final String SEPARATOR = "|";

    /** As operações que ficam registadas. */
    public enum Action {
        /** Criou o primeiro administrador. Valores: nome. */
        FIRST_ADMIN,
        /** Entrou no programa. */
        LOGIN,
        /** Palavra-passe errada. Valores: nome escrito. */
        LOGIN_FAILED,
        /** A conta ficou bloqueada por tentativas erradas. Valores: nome, até quando. */
        ACCOUNT_LOCKED,
        /** Terminou a sessão. */
        LOGOUT,
        /** Mudou a própria palavra-passe. */
        PASSWORD_CHANGED,
        /** Criou um utilizador. Valores: nome, perfil. */
        USER_CREATED,
        /** Repôs a palavra-passe de outro utilizador. Valores: nome. */
        PASSWORD_RESET,
        /** Desbloqueou uma conta. Valores: nome. */
        USER_UNLOCKED,
        /** Retirou um utilizador. Valores: nome. */
        USER_REMOVED,
        /** Mudou a tarifa. Valores: valor por hora, tecto diário, noite, desconto, multa, avença. */
        TARIFF_CHANGED,
        /** Saída sem talão, com multa. Valores: matrícula, talão, multa. */
        LOST_TICKET,
        /** Vendeu uma avença. Valores: matrícula, mês, valor. */
        PASS_SOLD,
        /** Cancelou uma reserva. Valores: matrícula. */
        RESERVATION_CANCELLED,
        /** Desfez uma entrada. Valores: matrícula, talão. */
        ENTRY_UNDONE,
        /** Desfez uma ida para a fila de espera. Valores: matrícula. */
        QUEUE_UNDONE,
        /** Desfez uma reserva. Valores: matrícula. */
        RESERVATION_UNDONE,
        /** Desfez o cancelamento de uma reserva. Valores: matrícula. */
        CANCELLATION_UNDONE,
        /** Fez uma cópia de segurança. Valores: ficheiro. */
        BACKUP_CREATED,
        /** Restaurou uma cópia de segurança. Valores: ficheiro. */
        BACKUP_RESTORED
    }

    private final LocalDateTime at;
    private final String username;
    private final Action action;
    private final String detail;
    private long id;

    /**
     * @param at quando
     * @param username quem (o nome escrito, num login falhado), ou {@code null}
     * @param action a operação
     * @param detail os valores da operação, separados por {@value #SEPARATOR};
     *        vazio se não houver
     */
    public AuditEntry(LocalDateTime at, String username, Action action, String detail) {
        this.at = Objects.requireNonNull(at, "at não pode ser null");
        this.action = Objects.requireNonNull(action, "action não pode ser null");
        this.username = username;
        this.detail = detail == null ? "" : detail;
    }

    /**
     * Junta os valores de uma operação com o separador.
     *
     * @param values os valores, por ordem
     * @return os valores separados por {@value #SEPARATOR}
     */
    public static String join(Object... values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                text.append(SEPARATOR);
            }
            text.append(values[i]);
        }
        return text.toString();
    }

    public LocalDateTime getAt() {
        return at;
    }

    /** @return quem fez a operação, ou {@code null} */
    public String getUsername() {
        return username;
    }

    public Action getAction() {
        return action;
    }

    /** @return os valores separados por {@value #SEPARATOR}, ou vazio */
    public String getDetail() {
        return detail;
    }

    public long getId() {
        return id;
    }

    /**
     * Regista o identificador atribuído pela base de dados. Só a camada
     * {@code persistence} chama este método, uma vez.
     *
     * @param newId o identificador, maior que 0
     */
    public void setId(long newId) {
        if (newId <= 0) {
            throw new IllegalArgumentException("id tem de ser positivo: " + newId);
        }
        if (id != 0) {
            throw new IllegalStateException("id já definido: " + id);
        }
        id = newId;
    }
}
