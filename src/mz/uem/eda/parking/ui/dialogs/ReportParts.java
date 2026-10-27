package mz.uem.eda.parking.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * As peças comuns aos ecrãs com tabelas ({@link ReportsDialog},
 * {@link CashCloseDialog}, {@link HistoryDialog}, {@link AuditDialog} e
 * outros): tabelas só de leitura com os números alinhados à direita, os
 * cartões com um número grande, a escolha do período e os formatos de data.
 * Existe só para os ecrãs não repetirem o mesmo código.
 */
final class ReportParts {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Os períodos que se podem escolher, pela ordem da lista. */
    static final String[] PERIODS = { "today", "yesterday", "last7", "last30", "thisMonth", "lastMonth",
        "all" };

    /** O período escolhido ao abrir: os últimos 7 dias. */
    static final int DEFAULT_PERIOD = 2;

    /** "Tudo" começa aqui; o programa não tem nada mais antigo. */
    static final LocalDate BEGINNING = LocalDate.of(2000, 1, 1);

    private ReportParts() {
    }

    /** Uma lista com os períodos de {@link #PERIODS}, já nos últimos 7 dias. */
    static JComboBox<String> periodChoice() {
        JComboBox<String> choice = new JComboBox<>();
        for (String key : PERIODS) {
            choice.addItem(Texts.get("report.period." + key));
        }
        choice.setSelectedIndex(DEFAULT_PERIOD);
        return choice;
    }

    /**
     * Os dias de um período da lista.
     *
     * @param index a posição em {@link #PERIODS}
     * @param today o dia de hoje
     * @return o primeiro dia (incluído) e o dia a seguir ao último (excluído)
     */
    static LocalDate[] period(int index, LocalDate today) {
        LocalDate first;
        LocalDate end = today.plusDays(1);
        switch (PERIODS[index]) {
            case "today":
                first = today;
                break;
            case "yesterday":
                first = today.minusDays(1);
                end = today;
                break;
            case "last7":
                first = today.minusDays(6);
                break;
            case "last30":
                first = today.minusDays(29);
                break;
            case "thisMonth":
                first = today.withDayOfMonth(1);
                break;
            case "lastMonth":
                first = today.withDayOfMonth(1).minusMonths(1);
                end = today.withDayOfMonth(1);
                break;
            default:
                first = BEGINNING;
                break;
        }
        return new LocalDate[] { first, end };
    }

    /**
     * O período escrito por extenso, para pôr ao lado da lista.
     *
     * @param first o primeiro dia
     * @param end o dia a seguir ao último
     */
    static String rangeText(LocalDate first, LocalDate end) {
        if (first.equals(BEGINNING)) {
            return Texts.format("report.range.all", end.minusDays(1).format(DATE));
        }
        return Texts.format("report.range", first.format(DATE), end.minusDays(1).format(DATE));
    }

    /**
     * Um modelo de tabela com os títulos das colunas tirados do
     * {@code strings_pt.properties}.
     *
     * @param columnKeys as chaves dos títulos, pela ordem das colunas
     */
    static DefaultTableModel model(String... columnKeys) {
        Object[] titles = new Object[columnKeys.length];
        for (int i = 0; i < columnKeys.length; i++) {
            titles[i] = Texts.get(columnKeys[i]);
        }
        return new DefaultTableModel(titles, 0);
    }

    /**
     * Uma tabela só de leitura, numa caixa com barra de deslocamento.
     *
     * @param model o modelo
     * @param rightAligned as colunas com números, alinhadas à direita
     */
    static JScrollPane table(DefaultTableModel model, int... rightAligned) {
        JTable table = new JTable(model);
        // Só leitura: sem editor, um duplo clique não abre a célula para escrever.
        table.setDefaultEditor(Object.class, null);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int column : rightAligned) {
            table.getColumnModel().getColumn(column).setCellRenderer(right);
        }
        return new JScrollPane(table);
    }

    /**
     * Dá a cada coluna da tabela a largura preferida, em píxeis; o resto do
     * espaço reparte-se na mesma proporção.
     *
     * @param scroll a caixa devolvida por {@link #table}
     * @param widths as larguras, pela ordem das colunas
     */
    static void widths(JScrollPane scroll, int... widths) {
        JTable table = (JTable) scroll.getViewport().getView();
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /**
     * Um cartão com um título pequeno e um número grande por baixo.
     *
     * @param title o título, já traduzido
     * @param value a etiqueta do número, que o ecrã vai actualizando
     */
    static JComponent card(String title, JLabel value) {
        JLabel caption = new JLabel(title);
        caption.setForeground(Theme.MUTED);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 22f));

        JPanel card = new JPanel(new BorderLayout(0, Theme.GAP / 2));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(Theme.GAP, Theme.PADDING, Theme.GAP, Theme.PADDING)));
        card.add(caption, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    /** Uma nota pequena e cinzenta, por baixo de uma tabela. */
    static JLabel note() {
        JLabel note = new JLabel(" ");
        note.setForeground(Theme.MUTED);
        return note;
    }

    /**
     * A parte de um valor no total, em percentagem com uma casa decimal:
     * 1 de 3 dá "33,3 %". Conta-se em inteiros para não haver erros de
     * arredondamento do {@code double}.
     */
    static String share(long part, long total) {
        if (total == 0) {
            return "—";
        }
        long tenths = (part * 1000 + total / 2) / total;
        return Texts.format("report.share", String.valueOf(tenths / 10), String.valueOf(tenths % 10));
    }
}
