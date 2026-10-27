package mz.uem.eda.parking.ui.dialogs;

import java.awt.BorderLayout;
import java.awt.Font;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
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
 * As peças comuns aos ecrãs de relatórios ({@link ReportsDialog} e
 * {@link CashCloseDialog}): tabelas só de leitura com os números alinhados
 * à direita, os cartões com um número grande e os formatos de data. Existe
 * só para os dois ecrãs não repetirem o mesmo código.
 */
final class ReportParts {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ReportParts() {
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
