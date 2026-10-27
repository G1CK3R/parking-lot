package mz.uem.eda.parking.demo;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Random;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.persistence.ConnectionFactory;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.service.policy.ByDeparture;
import mz.uem.eda.parking.service.policy.RoundRobin;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.dialogs.session.LoginDialog;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * Enche o parque até dois terços dos lugares, para demonstrações e para
 * testar os ecrãs com o parque cheio de carros. Não faz parte do programa
 * do balcão: corre-se à parte, com a configuração «Encher o parque (2/3)»
 * do VS Code, e depois abre-se o programa normal.
 *
 * <p>Não escreve na base directamente: pede o login (como o programa) e faz
 * cada entrada pela {@link ParkingDesk}, com as mesmas regras, a mesma
 * política de colocação ({@link ByDeparture}) e a mesma gravação. Por isso o
 * que fica na base é exactamente o que ficaria se um operador tivesse
 * registado estas entradas ao balcão.
 *
 * <p>Os carros são inventados, mas sempre os mesmos (gerador com semente
 * fixa): cerca de 10 % de curta duração, 80 % de jornada completa e 10 % de
 * permanência longa, com as entradas espalhadas pelas últimas horas, uma a
 * cada 2 minutos, a acabar agora. Matrículas que já estejam no parque são
 * saltadas, por isso correr duas vezes não dá erro: só completa o que falta
 * para chegar aos dois terços.
 */
public final class FillPark {

    /** A fracção dos lugares a ocupar. */
    private static final double TARGET = 2.0 / 3.0;

    /** Intervalo entre duas entradas, em minutos. */
    private static final int MINUTES_BETWEEN = 2;

    /** A semente do gerador: os mesmos carros em todas as máquinas. */
    private static final long SEED = 2026;

    private FillPark() {
    }

    /**
     * Abre o parque, pede o login e enche-o.
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
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        Theme.apply();

        ParkingDesk desk;
        try {
            desk = new ParkingDesk(ConnectionFactory.fromConfigFile(), new ByDeparture(), new RoundRobin(3),
                    LocalDateTime.now());
        } catch (IOException | IllegalArgumentException | SQLException e) {
            show(Texts.format("demo.error.open", e.getMessage()), JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (desk.needsFirstAdmin()) {
            show(Texts.get("demo.error.noUsers"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        User user = LoginDialog.ask(desk);
        if (user == null) {
            System.exit(0);
        }

        int target = (int) Math.round(desk.totalPlaces() * TARGET);
        int missing = target - desk.occupiedPlaces();
        if (missing <= 0) {
            show(Texts.format("demo.nothing", String.valueOf(desk.occupiedPlaces()),
                    String.valueOf(desk.totalPlaces())), JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Random random = new Random(SEED);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime entry = now.minusMinutes((long) missing * MINUTES_BETWEEN);
        int entered = 0;
        int attempts = 0;
        try {
            while (entered < missing && attempts < missing * 3) {
                attempts++;
                String plate = plate(random);
                if (desk.findForExit(plate) != null) {
                    continue;
                }
                ParkingSession session = desk.enter(plate, type(random), minutes(random), entry);
                if (session == null) {
                    break; // parque cheio: o carro ficou na fila de espera
                }
                entered++;
                entry = entry.plusMinutes(MINUTES_BETWEEN);
            }
            desk.logout();
        } catch (SQLException e) {
            show(Texts.format("demo.error.save", String.valueOf(entered), e.getMessage()),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        show(Texts.format("demo.done", String.valueOf(entered), String.valueOf(desk.occupiedPlaces()),
                String.valueOf(desk.totalPlaces())), JOptionPane.INFORMATION_MESSAGE);
    }

    /** Uma matrícula no formato de Moçambique: três letras, três algarismos e «MC». */
    private static String plate(Random random) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            text.append((char) ('A' + random.nextInt(26)));
        }
        text.append(' ').append(String.format("%03d", random.nextInt(1000))).append(" MC");
        return text.toString();
    }

    /** Três em cada quatro são ligeiros; o resto, motociclos e carrinhas. */
    private static Vehicle.Type type(Random random) {
        int draw = random.nextInt(100);
        if (draw < 75) {
            return Vehicle.Type.CAR;
        }
        return draw < 87 ? Vehicle.Type.MOTORCYCLE : Vehicle.Type.VAN;
    }

    /**
     * A duração declarada, em minutos: 10 % de curta duração (30 min a 2 h),
     * 80 % de jornada completa (3 h a 10 h, em meias horas) e 10 % de
     * permanência longa (2 a 5 dias).
     */
    private static int minutes(Random random) {
        int draw = random.nextInt(100);
        if (draw < 10) {
            return 30 + 15 * random.nextInt(7);
        }
        if (draw < 90) {
            return 180 + 30 * random.nextInt(15);
        }
        return (2 + random.nextInt(4)) * 24 * 60;
    }

    /** Mostra uma mensagem e fecha o programa. */
    private static void show(String text, int type) {
        JOptionPane.showMessageDialog(null, text, Texts.get("demo.title"), type);
        System.exit(0);
    }
}
