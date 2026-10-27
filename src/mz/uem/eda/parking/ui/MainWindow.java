package mz.uem.eda.parking.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.UIManager;

import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.dialogs.FirstAdminDialog;
import mz.uem.eda.parking.ui.dialogs.LoginDialog;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * A janela principal: menu em cima, o painel do parque ao centro e a barra
 * de estado em baixo, com quem está a trabalhar, a ocupação e a tarifa.
 *
 * <p>Ao centro está o {@link ParkPanel}. Os menus das operações e da
 * administração entram com os seus diálogos.
 *
 * <p>Fala só com a {@link ParkingDesk}: não conhece baias, pilhas, filas nem
 * a base de dados (ADR-032).
 */
public final class MainWindow {

    private final JFrame frame;
    private final ParkingDesk desk;
    private final ParkPanel parkPanel;
    private final JLabel userStatus = new JLabel();
    private final JLabel occupancyStatus = new JLabel();
    private final JLabel tariffStatus = new JLabel();

    /**
     * @param desk o balcão, já com um utilizador autenticado
     */
    public MainWindow(ParkingDesk desk) {
        this.desk = desk;
        this.frame = new JFrame(Texts.get("app.title"));

        frame.setJMenuBar(menuBar());
        this.parkPanel = new ParkPanel(desk);
        frame.add(parkPanel, BorderLayout.CENTER);
        frame.add(statusBar(), BorderLayout.SOUTH);
        refreshStatus();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1024, 640));
        frame.setSize(1200, 700);
        frame.setLocationRelativeTo(null);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    /**
     * Pede o login, ou a criação do primeiro administrador se a base ainda
     * não tiver utilizadores, e abre a janela principal. Se o operador
     * desistir, o programa fecha.
     *
     * <p>É o que o {@code Main} chama ao arrancar e o que "Terminar sessão"
     * chama para o próximo operador entrar.
     *
     * @param desk o balcão já aberto
     */
    public static void openAfterLogin(ParkingDesk desk) {
        User user;
        if (desk.needsFirstAdmin()) {
            user = FirstAdminDialog.ask(desk);
        } else {
            user = LoginDialog.ask(desk);
        }
        if (user == null) {
            System.exit(0);
        }
        new MainWindow(desk).frame.setVisible(true);
    }

    /**
     * Mostra o estado actual do parque: redesenha as baias e volta a
     * escrever a barra de estado. Os diálogos de entrada e saída vão
     * chamá-lo depois de cada operação.
     */
    public void refresh() {
        parkPanel.repaint();
        refreshStatus();
    }

    private void refreshStatus() {
        User user = desk.getCurrentUser();
        userStatus.setText(Texts.format("status.user", user.getUsername(),
                Texts.get("role." + user.getRole().name())));
        occupancyStatus.setText(Texts.format("status.occupancy",
                String.valueOf(desk.occupiedPlaces()), String.valueOf(desk.totalPlaces())));
        tariffStatus.setText(Texts.format("status.tariff",
                Texts.money(desk.getTariff().getHourlyRateCents())));
    }

    private JMenuBar menuBar() {
        JMenuItem logout = new JMenuItem(Texts.get("menu.session.logout"));
        logout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logout();
            }
        });
        JMenuItem exit = new JMenuItem(Texts.get("menu.session.exit"));
        exit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });

        JMenu session = new JMenu(Texts.get("menu.session"));
        session.add(logout);
        session.addSeparator();
        session.add(exit);

        JMenuBar bar = new JMenuBar();
        bar.add(session);
        return bar;
    }

    private JPanel statusBar() {
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.PADDING, 0));
        right.setOpaque(false);
        right.add(occupancyStatus);
        right.add(tariffStatus);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(Theme.GAP, Theme.PADDING, Theme.GAP, 0)));
        bar.add(userStatus, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** Termina a sessão, fecha a janela e pede o login do próximo operador. */
    private void logout() {
        desk.logout();
        frame.dispose();
        openAfterLogin(desk);
    }
}