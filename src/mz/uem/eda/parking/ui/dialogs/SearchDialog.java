package mz.uem.eda.parking.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.LocalDateTime;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.service.ParkedCar;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * A pesquisa de viaturas estacionadas (RF09), para qualquer utilizador:
 * escreve-se um pedaço da matrícula ou o número do talão e a lista vai-se
 * filtrando enquanto se escreve. Para cada carro mostra o tipo, a baia, quantos
 * carros tem por cima e quantas manobras custa tirá-lo, que é o que o
 * operador quer saber antes de chamar o condutor.
 *
 * <p>A procura é da {@link ParkingDesk#parkedCars}: uma busca sequencial
 * pelas baias, do topo para o fundo de cada uma. Com o parque cheio são
 * poucas dezenas de carros, por isso refazê-la a cada tecla não se nota.
 *
 * <p>«Dar saída…» (ou um duplo clique numa linha) fecha a pesquisa e
 * devolve o talão escolhido; a janela principal abre a saída já com esse
 * carro.
 */
public final class SearchDialog {

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final JTextField text = new JTextField(24);
    private final DefaultTableModel table = ReportParts.model("report.column.ticket", "report.column.plate",
            "report.column.type", "report.column.lane", "search.column.position", "search.column.manoeuvres", "report.column.entry",
            "report.column.stay");
    private final JScrollPane tableScroll = ReportParts.table(table, 0, 5, 7);
    private final JLabel note = ReportParts.note();
    private final JButton exit = new JButton(Texts.get("search.exit"));
    private String chosen;

    private SearchDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.dialog = new JDialog(owner, Texts.get("search.title"), Dialog.ModalityType.APPLICATION_MODAL);

        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
        tableOf().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                exit.setEnabled(tableOf().getSelectedRow() >= 0);
            }
        });
        tableOf().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && tableOf().getSelectedRow() >= 0) {
                    choose();
                }
            }
        });
        exit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                choose();
            }
        });
        JButton close = new JButton(Texts.get("report.close"));
        close.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });
        ReportParts.widths(tableScroll, 55, 100, 80, 50, 90, 80, 110, 80);

        JPanel searchRow = new JPanel(new BorderLayout(0, Theme.GAP / 2));
        searchRow.add(new JLabel(Texts.get("search.text")), BorderLayout.NORTH);
        searchRow.add(text, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP, 0));
        buttons.add(exit);
        buttons.add(close);
        JPanel bottom = new JPanel(new BorderLayout(Theme.PADDING, 0));
        bottom.add(note, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, Theme.PADDING));
        content.setBorder(Theme.padding());
        content.add(searchRow, BorderLayout.NORTH);
        content.add(tableScroll, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(880, 500);
        dialog.setLocationRelativeTo(owner);
        refresh();
    }

    /**
     * Mostra a pesquisa até o operador escolher um carro ou a fechar.
     *
     * @param owner a janela principal
     * @param desk o balcão
     * @return o número do talão do carro escolhido para dar saída, ou
     *         {@code null} se o operador só fechou a pesquisa
     */
    public static String ask(Window owner, ParkingDesk desk) {
        SearchDialog screen = new SearchDialog(owner, desk);
        screen.dialog.setVisible(true);
        return screen.chosen;
    }

    /** Refaz a pesquisa com o que está escrito. */
    private void refresh() {
        LocalDateTime now = LocalDateTime.now();
        table.setRowCount(0);
        for (ParkedCar car : desk.parkedCars(text.getText())) {
            ParkingSession session = car.getSession();
            String position = car.getDepth() == 0 ? Texts.get("search.position.top")
                    : Texts.format("search.position", String.valueOf(car.getDepth()));
            long stayed = Math.max(0, Duration.between(session.getEntryTime(), now).toMinutes());
            table.addRow(new Object[] { String.valueOf(session.getTicketNumber()), session.getVehicle().getPlate(),
                Texts.get("vehicle." + session.getVehicle().getType().name()), session.getLaneCode(), position, String.valueOf(car.getManoeuvres()),
                session.getEntryTime().format(ReportParts.DATE_TIME), Texts.duration(stayed) });
        }
        if (table.getRowCount() == 0) {
            note.setText(Texts.get("search.empty"));
        } else {
            note.setText(Texts.format("search.count", String.valueOf(table.getRowCount())));
        }
        exit.setEnabled(false);
    }

    private void choose() {
        int row = tableOf().getSelectedRow();
        if (row < 0) {
            return;
        }
        chosen = (String) table.getValueAt(row, 0);
        dialog.dispose();
    }

    private JTable tableOf() {
        return (JTable) tableScroll.getViewport().getView();
    }
}
