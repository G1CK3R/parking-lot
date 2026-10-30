package mz.uem.eda.parking.ui.dialogs.admin;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.table.DefaultTableModel;

import mz.uem.eda.parking.model.AuditEntry;
import mz.uem.eda.parking.model.User;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.dialogs.common.ReportParts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O registo de auditoria, só para o administrador (RF23): quem fez cada
 * operação sensível e quando (logins, palavras-passe, utilizadores, tarifa,
 * multas, avenças, cancelamentos, desfazer e cópias de segurança), com
 * filtros por período, por utilizador e por operação.
 *
 * <p>A {@code ParkingDesk} guarda os pormenores de cada linha só como
 * valores separados por {@value AuditEntry#SEPARATOR}; é este ecrã que os
 * escreve em português, com a frase {@code audit.detail.<operação>} de
 * {@code strings_pt.properties}. Os montantes vêm em centavos e as datas
 * em ISO, e são formatados aqui.
 *
 * <p>A linha mais recente fica em cima.
 */
public final class AuditDialog {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JComboBox<String> period = ReportParts.periodChoice();
    private final JLabel range = new JLabel();
    private final JComboBox<String> user = new JComboBox<>();
    private final JComboBox<String> action = new JComboBox<>();
    private final DefaultTableModel table = ReportParts.model("audit.column.at", "audit.column.user",
            "audit.column.action", "audit.column.detail");
    private final JLabel note = ReportParts.note();

    private AuditDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("audit.title"), Dialog.ModalityType.APPLICATION_MODAL);

        user.addItem(Texts.get("history.all"));
        for (User known : desk.users()) {
            user.addItem(known.getUsername());
        }
        action.addItem(Texts.get("audit.all"));
        for (AuditEntry.Action option : AuditEntry.Action.values()) {
            action.addItem(Texts.get("audit.action." + option.name()));
        }

        ActionListener reload = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                load();
            }
        };
        period.addActionListener(reload);
        user.addActionListener(reload);
        action.addActionListener(reload);
        JButton close = new JButton(Texts.get("report.close"));
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        filters.add(new JLabel(Texts.get("audit.period")));
        filters.add(period);
        filters.add(range);
        filters.add(new JLabel(Texts.get("audit.user")));
        filters.add(user);
        filters.add(new JLabel(Texts.get("audit.action")));
        filters.add(action);

        JScrollPane scroll = ReportParts.table(table);
        ReportParts.widths(scroll, 150, 110, 170, 520);

        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeRow.add(close);
        JPanel bottom = new JPanel(new BorderLayout(Theme.PADDING, 0));
        bottom.add(note, BorderLayout.CENTER);
        bottom.add(closeRow, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, Theme.PADDING));
        content.setBorder(Theme.padding());
        content.add(filters, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(1060, 560);
        dialog.setLocationRelativeTo(owner);
        load();
    }

    /**
     * Mostra o registo de auditoria até o administrador o fechar.
     *
     * @param owner a janela principal
     * @param desk o balcão, com um administrador autenticado
     */
    public static void show(Window owner, ParkingDesk desk) {
        new AuditDialog(owner, desk).dialog.setVisible(true);
    }

    /** Pede à {@code ParkingDesk} as linhas com os filtros escolhidos e mostra-as. */
    private void load() {
        LocalDate[] days = ReportParts.period(period.getSelectedIndex(), LocalDate.now());
        range.setText(ReportParts.rangeText(days[0], days[1]));
        String chosenUser = user.getSelectedIndex() <= 0 ? null : (String) user.getSelectedItem();
        AuditEntry.Action chosenAction = action.getSelectedIndex() <= 0 ? null
                : AuditEntry.Action.values()[action.getSelectedIndex() - 1];

        table.setRowCount(0);
        for (AuditEntry entry : desk.auditLog(days[0].atStartOfDay(), days[1].atStartOfDay(), chosenUser,
                chosenAction)) {
            // A fila vem da mais antiga para a mais recente; inserir no topo deixa a mais recente em cima.
            table.insertRow(0, new Object[] { entry.getAt().format(WHEN),
                entry.getUsername() == null ? "" : entry.getUsername(),
                Texts.get("audit.action." + entry.getAction().name()), describe(entry) });
        }
        note.setText(Texts.format("audit.count", String.valueOf(table.getRowCount())));
    }

    /**
     * Escreve os pormenores de uma linha em português. Os valores vêm pela
     * ordem descrita em {@link AuditEntry.Action}; os montantes, os meses,
     * as datas e os perfis são formatados antes de entrarem na frase.
     *
     * @param entry a linha
     * @return a frase, ou vazio se a operação não tiver pormenores
     */
    static String describe(AuditEntry entry) {
        if (entry.getDetail().isEmpty()) {
            return "";
        }
        String[] values = entry.getDetail().split("\\" + AuditEntry.SEPARATOR, -1);
        switch (entry.getAction()) {
            case ACCOUNT_LOCKED:
                values[1] = formatDateTime(values[1]);
                break;
            case USER_CREATED:
                values[1] = Texts.get("role." + values[1]);
                break;
            case TARIFF_CHANGED:
                values[0] = formatMoney(values[0]);
                values[1] = formatMoney(values[1]);
                values[2] = formatMoney(values[2]);
                values[4] = formatMoney(values[4]);
                values[5] = formatMoney(values[5]);
                break;
            case POLICIES_CHANGED:
                values[0] = Texts.get("policies.placement." + values[0]);
                values[1] = Texts.format("policies.service." + values[1], values[2]);
                break;
            case LOST_TICKET:
                values[2] = formatMoney(values[2]);
                break;
            case PASS_SOLD:
                values[1] = Texts.month(YearMonth.parse(values[1]));
                values[2] = formatMoney(values[2]);
                break;
            default:
                break;
        }
        return Texts.format("audit.detail." + entry.getAction().name(), (Object[]) values);
    }

    private static String formatMoney(String cents) {
        return Texts.money(Long.parseLong(cents));
    }

    private static String formatDateTime(String iso) {
        return LocalDateTime.parse(iso).format(ReportParts.DATE_TIME);
    }
}
