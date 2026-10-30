package mz.uem.eda.parking.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.sql.SQLException;
import java.time.LocalDateTime;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.service.views.ExitResult;
import mz.uem.eda.parking.service.views.Operation;
import mz.uem.eda.parking.ui.dialogs.admin.AuditDialog;
import mz.uem.eda.parking.ui.dialogs.admin.BackupActions;
import mz.uem.eda.parking.ui.dialogs.admin.HistoryDialog;
import mz.uem.eda.parking.ui.dialogs.admin.PoliciesDialog;
import mz.uem.eda.parking.ui.dialogs.admin.ReportsDialog;
import mz.uem.eda.parking.ui.dialogs.admin.SimulationDialog;
import mz.uem.eda.parking.ui.dialogs.admin.TariffDialog;
import mz.uem.eda.parking.ui.dialogs.admin.UsersDialog;
import mz.uem.eda.parking.ui.dialogs.common.Errors;
import mz.uem.eda.parking.ui.dialogs.operations.CashCloseDialog;
import mz.uem.eda.parking.ui.dialogs.operations.EntryDialog;
import mz.uem.eda.parking.ui.dialogs.operations.ExitDialog;
import mz.uem.eda.parking.ui.dialogs.operations.PassesDialog;
import mz.uem.eda.parking.ui.dialogs.operations.ReservationsDialog;
import mz.uem.eda.parking.ui.dialogs.operations.SearchDialog;
import mz.uem.eda.parking.ui.dialogs.session.FirstAdminDialog;
import mz.uem.eda.parking.ui.dialogs.session.LoginDialog;
import mz.uem.eda.parking.ui.dialogs.session.PasswordDialog;
import mz.uem.eda.parking.ui.panels.MoveAnimator;
import mz.uem.eda.parking.ui.panels.ParkPanel;
import mz.uem.eda.parking.ui.panels.ParkScene;
import mz.uem.eda.parking.ui.panels.SidePanel;
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
 * <p>O menu Operações tem também a pesquisa de viaturas (F7), as reservas
 * (F5), as avenças (F6), o desfazer (Ctrl+Z) e o fecho de caixa (F4): o que
 * quem está na sessão cobrou desde que entrou. A hora de entrada é a hora a
 * que esta janela foi criada, logo depois do login.
 *
 * <p><b>Desfazer.</b> O item do menu diz o que vai desfazer («Desfazer a
 * entrada de AAA123MC»), lido da pilha de operações da
 * {@code ParkingDesk} quando o menu abre. Desfazer uma entrada pede
 * confirmação, porque anula o talão.
 *
 * <p><b>Fila de espera.</b> Um relógio de 30 segundos pede à
 * {@code ParkingDesk} que deixe entrar os carros em espera, para os lugares
 * que ficam livres sem haver uma saída (uma reserva que expira, por
 * exemplo). Não faz nada enquanto houver um diálogo aberto ou uma
 * animação a decorrer, para não mudar o parque por baixo do operador.
 *
 * <p>O menu Sessão tem, para todos, a mudança da própria palavra-passe. O
 * menu Administração (utilizadores, tarifa, políticas, relatórios, histórico,
 * auditoria, simulação, cópia de segurança e restauro) só existe na janela
 * de um administrador: a janela constrói-se de novo a cada login, por isso
 * um operador nunca o vê. A {@code ParkingDesk} volta a recusar estas
 * operações a quem não for administrador; o menu escondido é só para não
 * oferecer o que vai ser recusado.
 *
 * <p>Fala só com a {@link ParkingDesk}: não conhece baias, pilhas, filas nem
 * a base de dados (ADR-032).
 */
public final class MainWindow {

    /** De quanto em quanto tempo se tenta admitir carros da fila de espera. */
    private static final int WAITING_CHECK_MS = 30_000;

    private final JFrame frame;
    private final ParkingDesk desk;
    private final ParkPanel parkPanel;
    private final MoveAnimator animator;
    private final SidePanel sidePanel;
    private final JMenuItem entryItem = new JMenuItem();
    private final JMenuItem exitItem = new JMenuItem();
    private final JMenuItem undoItem = new JMenuItem();
    private final JButton entryButton = new JButton();
    private final JButton exitButton = new JButton();
    private final JButton undoButton = new JButton();
    private final JLabel userStatus = new JLabel();
    private final JLabel occupancyStatus = new JLabel();
    private final JLabel tariffStatus = new JLabel();
    private final JLabel policiesStatus = new JLabel();
    private final JLabel reservationStatus = new JLabel();
    private final Timer waitingClock;
    /** Quando começou o turno de quem está na sessão: a hora do login. */
    private final LocalDateTime shiftStart = LocalDateTime.now();

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
        updateUndo();

        waitingClock = new Timer(WAITING_CHECK_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                admitWaiting();
            }
        });
        waitingClock.start();

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
        updateUndo();
    }

    private void refreshStatus() {
        User user = desk.getCurrentUser();
        if (user == null) {
            return;
        }
        userStatus.setText(Texts.format("status.user", user.getUsername(),
                Texts.get("role." + user.getRole().name())));
        occupancyStatus.setText(Texts.format("status.occupancy",
                String.valueOf(desk.occupiedPlaces()), String.valueOf(desk.totalPlaces())));
        reservationStatus.setText(Texts.format("status.reservations",
                String.valueOf(desk.activeReservationCount())));
        tariffStatus.setText(Texts.format("status.tariff",
                Texts.money(desk.getTariff().getHourlyRateCents())));
        String[] policies = PoliciesDialog.names(desk.getPolicies());
        policiesStatus.setText(Texts.format("status.policies", policies[0], policies[1]));
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
                openExit(null);
            }
        });

        JMenuItem search = new JMenuItem(Texts.get("menu.operations.search"));
        search.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
        search.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openSearch();
            }
        });

        JMenuItem reservations = new JMenuItem(Texts.get("menu.operations.reservations"));
        reservations.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        reservations.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ReservationsDialog.show(frame, desk);
                refresh();
            }
        });

        JMenuItem passes = new JMenuItem(Texts.get("menu.operations.passes"));
        passes.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
        passes.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PassesDialog.show(frame, desk);
            }
        });

        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        undoItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                undo();
            }
        });

        JMenuItem cashClose = new JMenuItem(Texts.get("menu.operations.cash"));
        cashClose.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0));
        cashClose.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                CashCloseDialog.show(frame, desk, shiftStart);
            }
        });

        JMenu operations = new JMenu(Texts.get("menu.operations"));
        operations.add(entry);
        operations.add(carOut);
        operations.add(search);
        operations.addSeparator();
        operations.add(reservations);
        operations.add(passes);
        operations.addSeparator();
        operations.add(undoItem);
        operations.addSeparator();
        operations.add(cashClose);
        // O texto do desfazer diz o que vai ser desfeito; lê-se quando o menu abre.
        operations.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                updateUndo();
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });

        JMenuBar bar = new JMenuBar();
        bar.add(session);
        bar.add(operations);
        if (desk.getCurrentUser().getRole() == User.Role.ADMIN) {
            bar.add(adminMenu());
        }
        return bar;
    }

    /**
     * O menu só do administrador: utilizadores, tarifa, políticas, relatórios,
     * histórico, auditoria, simulação, cópia de segurança e restauro.
     */
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

        JMenuItem policies = new JMenuItem(Texts.get("menu.admin.policies"));
        policies.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (PoliciesDialog.ask(frame, desk)) {
                    refreshStatus();
                }
            }
        });

        JMenuItem reports = new JMenuItem(Texts.get("menu.admin.reports"));
        reports.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ReportsDialog.show(frame, desk);
            }
        });
        JMenuItem history = new JMenuItem(Texts.get("menu.admin.history"));
        history.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                HistoryDialog.show(frame, desk);
            }
        });
        JMenuItem audit = new JMenuItem(Texts.get("menu.admin.audit"));
        audit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AuditDialog.show(frame, desk);
            }
        });

        JMenuItem simulation = new JMenuItem(Texts.get("menu.admin.simulation"));
        simulation.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SimulationDialog.show(frame);
            }
        });

        JMenuItem backup = new JMenuItem(Texts.get("menu.admin.backup"));
        backup.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                BackupActions.backup(frame, desk);
            }
        });
        JMenuItem restore = new JMenuItem(Texts.get("menu.admin.restore"));
        restore.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (BackupActions.restore(frame, desk)) {
                    // O que está em memória já não é o que está na base: fecha-se o programa.
                    System.exit(0);
                }
            }
        });

        JMenu admin = new JMenu(Texts.get("menu.admin"));
        admin.add(users);
        admin.add(tariff);
        admin.add(policies);
        admin.addSeparator();
        admin.add(reports);
        admin.add(history);
        admin.add(audit);
        admin.add(simulation);
        admin.addSeparator();
        admin.add(backup);
        admin.add(restore);
        return admin;
    }

    /** Os botões por cima do parque, para as operações do dia-a-dia. */
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
                openExit(null);
            }
        });
        undoButton.setText(Texts.get("toolbar.undo"));
        undoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                undo();
            }
        });

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(Theme.GAP / 2, Theme.PADDING, Theme.GAP / 2, Theme.PADDING));
        bar.add(entry);
        bar.addSeparator();
        bar.add(carOut);
        bar.addSeparator();
        bar.add(undoButton);
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
     *
     * @param plateOrTicket o carro a procurar logo (vindo da pesquisa), ou
     *        {@code null}
     */
    private void openExit(String plateOrTicket) {
        if (animator.isRunning()) {
            return;
        }
        ParkScene before = ParkScene.capture(desk.lanes());
        final ExitResult result = ExitDialog.ask(frame, desk, plateOrTicket);
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

    /** Pesquisa de viaturas; se o operador escolher um carro, abre a saída com ele. */
    private void openSearch() {
        if (animator.isRunning()) {
            return;
        }
        String ticket = SearchDialog.ask(frame, desk);
        if (ticket != null) {
            openExit(ticket);
        }
    }

    /**
     * Desfaz a última entrada ou reserva. Uma entrada pede confirmação,
     * porque o talão fica anulado.
     */
    private void undo() {
        if (animator.isRunning() || !desk.canUndo()) {
            return;
        }
        Operation last = desk.lastOperation();
        if (last.getType() == Operation.Type.ENTRY) {
            Object[] options = { Texts.get("undo.confirm"), Texts.get("dialog.cancel") };
            int answer = JOptionPane.showOptionDialog(frame,
                    Texts.format("undo.entry.question", last.getPlate(),
                            String.valueOf(last.getSession().getTicketNumber())),
                    Texts.get("menu.operations.undo"), JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                    null, options, options[1]);
            if (answer != 0) {
                return;
            }
        }
        try {
            desk.undo(LocalDateTime.now());
        } catch (IllegalStateException e) {
            // Por exemplo, a reserva cancelada já expirou e não pode voltar.
            JOptionPane.showMessageDialog(frame, Texts.format("undo.failed", e.getMessage()),
                    Texts.get("menu.operations.undo"), JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            Errors.saveFailed(frame, e);
        }
        refresh();
    }

    /** Põe no item e no botão de desfazer o que vai ser desfeito, ou desliga-os. */
    private void updateUndo() {
        Operation last = desk.canUndo() ? desk.lastOperation() : null;
        boolean enabled = last != null && !animator.isRunning();
        if (last == null) {
            undoItem.setText(Texts.get("menu.operations.undo"));
            undoButton.setToolTipText(Texts.get("toolbar.undo.none"));
        } else {
            String text = Texts.format("menu.operations.undo." + last.getType().name(), last.getPlate());
            undoItem.setText(text);
            undoButton.setToolTipText(text);
        }
        undoItem.setEnabled(enabled);
        undoButton.setEnabled(enabled);
    }

    /**
     * Deixa entrar os carros em espera para os lugares livres sem reserva.
     * Não faz nada com um diálogo aberto ou durante uma animação.
     */
    private void admitWaiting() {
        if (!frame.isShowing() || animator.isRunning() || hasOpenDialog() || desk.getCurrentUser() == null) {
            return;
        }
        try {
            desk.admitWaiting(LocalDateTime.now());
        } catch (SQLException e) {
            waitingClock.stop();
            Errors.saveFailed(frame, e);
        }
        // Também as reservas que expiraram desaparecem da contagem na barra de estado.
        desk.freeForWalkIns(LocalDateTime.now());
        refresh();
    }

    /** @return {@code true} se algum diálogo da janela estiver aberto */
    private boolean hasOpenDialog() {
        for (Window owned : frame.getOwnedWindows()) {
            if (owned.isShowing()) {
                return true;
            }
        }
        return false;
    }

    /** Liga ou desliga a entrada, a saída e o desfazer (desligados durante a animação). */
    private void setOperationsEnabled(boolean enabled) {
        entryItem.setEnabled(enabled);
        exitItem.setEnabled(enabled);
        entryButton.setEnabled(enabled);
        exitButton.setEnabled(enabled);
        if (enabled) {
            updateUndo();
        } else {
            undoItem.setEnabled(false);
            undoButton.setEnabled(false);
        }
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
        right.add(reservationStatus);
        right.add(tariffStatus);
        right.add(policiesStatus);

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
        waitingClock.stop();
        try {
            desk.logout();
        } catch (SQLException e) {
            // A sessão termina na mesma; só a linha do registo de auditoria não ficou gravada.
            Errors.saveFailed(frame, e);
        }
        frame.dispose();
        openAfterLogin(desk);
    }
}
