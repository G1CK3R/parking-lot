package mz.uem.eda.parking;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Locale;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import mz.uem.eda.parking.persistence.ConnectionFactory;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.service.policy.ByDeparture;
import mz.uem.eda.parking.service.policy.RoundRobin;
import mz.uem.eda.parking.ui.MainWindow;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * Arranca a aplicação. É o único sítio que conhece todas as peças: lê a
 * configuração, abre o parque com as políticas escolhidas e entrega-o à
 * interface.
 *
 * <p>Políticas: {@link ByDeparture}, a colocação com menos manobras na
 * simulação (decidido a 27 Out), e {@link RoundRobin} com três preferenciais
 * por cada normal, a mesma rotação que o simulador compara.
 *
 * <p>Se a base de dados não abrir, mostra o motivo numa mensagem e fecha. O
 * motivo distingue os casos que a equipa vai encontrar ao montar o projecto:
 * falta o {@code config.properties}, o MySQL está desligado, as credenciais
 * estão erradas, a base não foi criada, ou o esquema está desactualizado.
 */
public class Main {

    /** Quantos carros preferenciais seguidos antes de um normal. */
    private static final int PREFERENTIAL_TURNS = 3;

    /** Códigos de erro do MySQL que valem uma mensagem própria. */
    private static final int ACCESS_DENIED = 1045;
    private static final int UNKNOWN_DATABASE = 1049;
    private static final int UNKNOWN_COLUMN = 1054;
    private static final int UNKNOWN_TABLE = 1146;

    /**
     * Ponto de entrada. Tudo o que mexe na interface corre na EDT, por isso
     * o arranque é entregue ao {@link SwingUtilities#invokeLater}.
     *
     * @param args não usados
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                start();
            }
        });
    }

    private static void start() {
        // Os textos do próprio Swing (escolher ficheiro, Sim, Não, Cancelar)
        // saem em português. O Java só traz a tradução do Brasil; os textos do
        // programa continuam a vir de strings_pt.properties.
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        Theme.apply();

        ConnectionFactory connections;
        try {
            connections = ConnectionFactory.fromConfigFile();
        } catch (IOException | IllegalArgumentException e) {
            fail(Texts.format("startup.error.config", e.getMessage()));
            return;
        }

        ParkingDesk desk;
        try {
            desk = new ParkingDesk(connections, new ByDeparture(),
                    new RoundRobin(PREFERENTIAL_TURNS), LocalDateTime.now());
        } catch (SQLException e) {
            fail(reason(e, connections.getUrl()));
            return;
        }

        MainWindow.openAfterLogin(desk);
    }

    /**
     * Traduz um erro da base de dados no motivo que o operador lê.
     *
     * @param e o erro devolvido pelo controlador JDBC
     * @param url o endereço da base, para a mensagem
     * @return o texto a mostrar
     */
    private static String reason(SQLException e, String url) {
        String state = e.getSQLState();
        if (state != null && state.startsWith("08")) {
            // Estado 08: a ligação ao servidor não se fez.
            return Texts.format("startup.error.unreachable", url);
        }
        switch (e.getErrorCode()) {
            case ACCESS_DENIED:
                return Texts.get("startup.error.access");
            case UNKNOWN_DATABASE:
                return Texts.get("startup.error.nodatabase");
            case UNKNOWN_TABLE:
            case UNKNOWN_COLUMN:
                return Texts.get("startup.error.schema");
            default:
                return Texts.format("startup.error.other", url, e.getMessage());
        }
    }

    private static void fail(String message) {
        JOptionPane.showMessageDialog(null, message, Texts.get("startup.error.title"),
                JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }
}