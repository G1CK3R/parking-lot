package mz.uem.eda.parking.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JComponent;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.ExitResult;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.dialogs.EntryDialog;
import mz.uem.eda.parking.ui.dialogs.ExitDialog;
import mz.uem.eda.parking.ui.dialogs.FirstAdminDialog;
import mz.uem.eda.parking.ui.dialogs.LoginDialog;
import mz.uem.eda.parking.ui.dialogs.PasswordDialog;
import mz.uem.eda.parking.ui.dialogs.TariffDialog;
import mz.uem.eda.parking.ui.dialogs.UsersDialog;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * A janela principal: menu e barra de botões em cima, o painel do parque ao
 * centro, as filas de espera e os últimos movimentos à direita
 * ({@link SidePanel}) e a barra de estado em baixo, com quem está a
 * trabalhar, a ocupação e a tarifa.
 *
 * <p>A entrada e a saída abrem-se pelo menu Operações, pelos botões por
 * cima do parque ou pelas teclas F2 e F3. Depois de cada operação, o
 * {@link MoveAnimator} reproduz os movimentos no painel e só no fim aparece
 * o talão ou o recibo. Durante a animação as operações ficam bloqueadas; a
 * tecla Esc salta a animação.
 *
 * <p>O menu Sessão tem, para todos, a mudança da própria palavra-passe. O
 * menu Administração (utilizadores e tarifa) só existe na janela de um
 * administrador: a janela constrói-se de novo a cada login, por isso um
 * operador nunca o vê. A {@code ParkingDesk} volta a recusar estas
 * operações a quem não for administrador; o menu escondido é só para não
 * oferecer o que vai ser recusado.
 *
 * <p>Fala só com a {@link ParkingDesk}: não conhece baias, pilhas, filas nem
 * a base de dados (ADR-032).
 */
public final class MainWindow {

    private final JFrame frame;
    private final ParkingDesk desk;
    private final ParkPanel parkPanel;
    private final MoveAnimator animator;
    private final SidePanel sidePanel;
    private final JMenuItem entryItem = new JMenuItem();
    private final JMenuItem exitItem = new JMenuItem();
    private final JButton entryButton = new JButton();
    private final JButton exitButton = new JButton();
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
        frame.add(toolBar(), BorderLayout.NORTH);
        this.parkPanel = new ParkPanel(desk);
        this.animator = new MoveAnimator(parkPanel, desk);
        frame.add(parkPanel, BorderLayout.CENTER);
        this.sidePanel = new SidePanel(desk);
        frame.add(sidePanel.getComponent(), BorderLayout.EAST);
        bindSkipKey();
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
     * Mostra o estado actual do parque: redesenha as baias, as filas de
     * espera, os últimos movimentos e a barra de estado. Chama-se depois de
     * cada operação, quando a animação acaba.
     */
    public void refresh() {
        parkPanel.repaint();
        sidePanel.refresh();
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

        JMenuItem password = new JMenuItem(Texts.get("menu.session.password"));
        password.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PasswordDialog.askOwn(frame, desk);
            }
        });

        JMenu session = new JMenu(Texts.get("menu.session"));
        session.add(password);
        session.add(logout);
        session.addSeparator();
        session.add(exit);

        JMenuItem entry = entryItem;
        entry.setText(Texts.get("menu.operations.entry"));
        entry.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0));
        entry.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openEntry();
            }
        });
        JMenuItem carOut = exitItem;
        carOut.setText(Texts.get("menu.operations.exit"));
        carOut.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F3, 0));
        carOut.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openExit();
            }
        });

        JMenu operations = new JMenu(Texts.get("menu.operations"));
        operations.add(entry);
        operations.add(carOut);

        JMenuBar bar = new JMenuBar();
        bar.add(session);
        bar.add(operations);
        if (desk.getCurrentUser().getRole() == User.Role.ADMIN) {
            bar.add(adminMenu());
        }
        return bar;
    }

    /** O menu só do administrador: utilizadores e tarifa. */
    private JMenu adminMenu() {
        JMenuItem users = new JMenuItem(Texts.get("menu.admin.users"));
        users.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                UsersDialog.show(frame, desk);
            }
        });
        JMenuItem tariff = new JMenuItem(Texts.get("menu.admin.tariff"));
        tariff.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (TariffDialog.ask(frame, desk)) {
                    refreshStatus();
                }
            }
        });

        JMenu admin = new JMenu(Texts.get("menu.admin"));
        admin.add(users);
        admin.add(tariff);
        return admin;
    }

    /** Os dois botões por cima do parque, para as operações do dia-a-dia. */
    private JToolBar toolBar() {
        JButton entry = entryButton;
        entry.setText(Texts.get("toolbar.entry"));
        entry.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openEntry();
            }
        });
        JButton carOut = exitButton;
        carOut.setText(Texts.get("toolbar.exit"));
        carOut.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openExit();
            }
        });

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(Theme.GAP / 2, Theme.PADDING, Theme.GAP / 2, Theme.PADDING));
        bar.add(entry);
        bar.addSeparator();
        bar.add(carOut);
        return bar;
    }

    /**
     * Entrada: tira a fotografia do parque, abre o diálogo e, se o carro
     * entrou, anima-o da cancela até à baia e mostra o talão no fim.
     */
    private void openEntry() {
        if (animator.isRunning()) {
            return;
        }
        ParkScene before = ParkScene.capture(desk.lanes());
        final ParkingSession session = EntryDialog.ask(frame, desk);
        if (session == null) {
            // Cancelada, ou o carro ficou na fila de espera: não há nada para animar.
            refresh();
            return;
        }
        // O painel lateral só muda no fim, para não mostrar o movimento antes de o animar.
        refreshStatus();
        setOperationsEnabled(false);
        animator.playEntry(before, session, new Runnable() {
            @Override
            public void run() {
                setOperationsEnabled(true);
                refresh();
                EntryDialog.showTicket(frame, session);
            }
        });
    }

    /**
     * Saída: tira a fotografia do parque, abre o diálogo e, se o carro
     * saiu, reproduz as manobras e mostra o recibo no fim.
     */
    private void openExit() {
        if (animator.isRunning()) {
            return;
        }
        ParkScene before = ParkScene.capture(desk.lanes());
        final ExitResult result = ExitDialog.ask(frame, desk);
        if (result == null) {
            refresh();
            return;
        }
        refreshStatus();
        setOperationsEnabled(false);
        animator.playExit(before, result, new Runnable() {
            @Override
            public void run() {
                setOperationsEnabled(true);
                refresh();
                ExitDialog.showReceipt(frame, result);
            }
        });
    }

    /** Liga ou desliga a entrada e a saída (desligadas durante a animação). */
    private void setOperationsEnabled(boolean enabled) {
        entryItem.setEnabled(enabled);
        exitItem.setEnabled(enabled);
        entryButton.setEnabled(enabled);
        exitButton.setEnabled(enabled);
    }

    /** A tecla Esc, em qualquer ponto da janela, salta a animação em curso. */
    private void bindSkipKey() {
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "skipAnimation");
        root.getActionMap().put("skipAnimation", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                animator.finish();
            }
        });
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
        animator.finish();
        sidePanel.stop();
        desk.logout();
        frame.dispose();
        openAfterLogin(desk);
    }
}