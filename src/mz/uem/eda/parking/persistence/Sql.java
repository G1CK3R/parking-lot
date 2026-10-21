package mz.uem.eda.parking.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Pequenas peças comuns aos repositórios: datas e ids gerados. Só é usada
 * dentro do pacote {@code persistence}.
 *
 * <p>As colunas {@code DATETIME} guardam segundos. O MySQL arredondaria as
 * fracções de segundo (0,5 s ou mais sobe um segundo), o que mudaria a hora
 * de entrada ao reabrir. Por isso as horas são cortadas ao segundo antes de
 * gravar: depois de reabrir, a hora é a mesma, quando muito com menos de um
 * segundo de diferença para a que estava em memória.
 */
final class Sql {

    private Sql() {
        // só métodos estáticos
    }

    /** Grava uma hora, cortada ao segundo, ou {@code NULL}. */
    static void setTime(PreparedStatement st, int index, LocalDateTime time) throws SQLException {
        if (time == null) {
            st.setNull(index, Types.TIMESTAMP);
        } else {
            st.setObject(index, time.truncatedTo(ChronoUnit.SECONDS));
        }
    }

    /** Lê uma hora, ou {@code null} se a coluna estiver vazia. */
    static LocalDateTime getTime(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    /** O id gerado pelo último {@code INSERT} com {@code RETURN_GENERATED_KEYS}. */
    static long generatedKey(Statement st) throws SQLException {
        try (ResultSet keys = st.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new SQLException("a base não devolveu o id gerado");
            }
            return keys.getLong(1);
        }
    }

    /** Desfaz a transacção sem esconder o erro original. */
    static void rollbackQuietly(java.sql.Connection c, SQLException cause) {
        try {
            c.rollback();
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
    }
}