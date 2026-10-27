package mz.uem.eda.parking.ui.dialogs.common;

import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import mz.uem.eda.parking.ui.theme.Theme;

/**
 * O esqueleto comum dos formulários dos diálogos: uma coluna com cada
 * etiqueta por cima do seu campo, uma linha para mensagens de erro e os
 * botões alinhados à direita. Existe só para os diálogos não repetirem o
 * mesmo {@code GridBagLayout}.
 */
public class FormPanel {

    private final JPanel panel = new JPanel(new GridBagLayout());
    private final GridBagConstraints next = new GridBagConstraints();

    public FormPanel() {
        panel.setBorder(Theme.padding());
        next.gridx = 0;
        next.gridy = 0;
        next.fill = GridBagConstraints.HORIZONTAL;
        next.weightx = 1;
        next.anchor = GridBagConstraints.WEST;
    }

    /** Acrescenta um texto corrido, por exemplo uma explicação no topo. */
    public void addText(String text) {
        addRow(new JLabel(text), Theme.GAP * 2);
    }

    /** Acrescenta uma etiqueta e, por baixo, o campo a que se refere. */
    public void addField(String label, JComponent field) {
        JLabel title = new JLabel(label);
        title.setLabelFor(field);
        addRow(title, Theme.GAP / 2);
        addRow(field, Theme.GAP + Theme.GAP / 2);
    }

    /** Acrescenta um campo sem etiqueta por cima, por exemplo uma caixa de escolha. */
    public void addComponent(JComponent field) {
        addRow(field, Theme.GAP + Theme.GAP / 2);
    }

    /**
     * Acrescenta a linha das mensagens de erro, a vermelho. Começa com um
     * espaço para já ocupar a altura de uma linha e o diálogo não mudar de
     * tamanho quando aparece uma mensagem.
     */
    public void addMessage(JLabel message) {
        message.setText(" ");
        message.setForeground(Theme.ERROR);
        addRow(message, Theme.GAP);
    }

    /** Acrescenta os botões, alinhados à direita, pela ordem dada. */
    public void addButtons(JButton... buttons) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP, 0));
        for (JButton button : buttons) {
            row.add(button);
        }
        addRow(row, 0);
    }

    /** @return o painel com o formulário, para pôr no diálogo */
    public JPanel getPanel() {
        return panel;
    }

    private void addRow(JComponent component, int spaceBelow) {
        next.insets = new Insets(0, 0, spaceBelow, 0);
        panel.add(component, next);
        next.gridy++;
    }
}