package mz.uem.eda.parking.ui.dialogs;

import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.time.LocalDateTime;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import mz.uem.eda.parking.model.Tariff;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;

/**
 * Muda a tarifa (RF13), só para o administrador. Os quatro valores começam
 * com os da tarifa em vigor: tolerância gratuita, fracção mínima de
 * cobrança, valor por hora e tecto diário.
 *
 * <p>Por baixo, uma tabela de exemplos mostra quanto pagam algumas estadias
 * com a tarifa actual e com a nova, e muda enquanto se escreve. As contas
 * são da {@link ParkingDesk#priceWith}, com as mesmas regras da cobrança
 * verdadeira; o ecrã não calcula preços.
 *
 * <p>Os valores escrevem-se em meticais ("60" ou "60,50") e guardam-se em
 * centavos, num {@code long}, como em todo o programa (ver {@link Tariff}).
 *
 * <p>A tarifa nova vale para todas as saídas a partir de agora, incluindo
 * as dos carros que já estão no parque. Os pagamentos antigos continuam
 * ligados à tarifa com que foram calculados (ADR-031).
 */
public final class TariffDialog {

    /** As estadias dos exemplos, em minutos: 10 min, 45 min, 1 h 10, 3 h, 10 h e 26 h. */
    private static final int[] EXAMPLES = { 10, 45, 70, 180, 600, 1560 };

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final Tariff active;
    private final JSpinner freeMinutes;
    private final JSpinner fractionMinutes;
    private final JTextField hourly = new JTextField(10);
    private final JTextField dailyCap = new JTextField(10);
    private final JLabel examples = new JLabel();
    private final JLabel message = new JLabel();
    private boolean done;

    private TariffDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.active = desk.getTariff();
        this.dialog = new JDialog(owner, Texts.get("tariff.title"), Dialog.ModalityType.APPLICATION_MODAL);

        freeMinutes = new JSpinner(new SpinnerNumberModel(active.getFreeMinutes(), 0, 240, 5));
        fractionMinutes = new JSpinner(new SpinnerNumberModel(active.getFractionMinutes(), 1, 60, 1));
        hourly.setText(meticais(active.getHourlyRateCents()));
        dailyCap.setText(meticais(active.getDailyCapCents()));

        ActionListener save = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        };
        JButton submit = new JButton(Texts.get("tariff.submit"));
        submit.addActionListener(save);
        // Enter dentro de um campo numérico também grava, como nos outros campos.
        ((JSpinner.DefaultEditor) freeMinutes.getEditor()).getTextField().addActionListener(save);
        ((JSpinner.DefaultEditor) fractionMinutes.getEditor()).getTextField().addActionListener(save);
        JButton cancel = new JButton(Texts.get("dialog.cancel"));
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        // Os exemplos acompanham o que se escreve.
        ChangeListener spinnerChanged = new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                updateExamples();
            }
        };
        freeMinutes.addChangeListener(spinnerChanged);
        fractionMinutes.addChangeListener(spinnerChanged);
        DocumentListener textChanged = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateExamples();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateExamples();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateExamples();
            }
        };
        hourly.getDocument().addDocumentListener(textChanged);
        dailyCap.getDocument().addDocumentListener(textChanged);

        FormPanel form = new FormPanel();
        form.addText(Texts.get("tariff.intro"));
        form.addField(Texts.get("tariff.free"), freeMinutes);
        form.addField(Texts.get("tariff.fraction"), fractionMinutes);
        form.addField(Texts.get("tariff.hourly"), hourly);
        form.addField(Texts.get("tariff.cap"), dailyCap);
        form.addField(Texts.get("tariff.examples"), examples);
        form.addMessage(message);
        form.addButtons(cancel, submit);

        dialog.setContentPane(form.getPanel());
        dialog.getRootPane().setDefaultButton(submit);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        updateExamples();
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
    }

    /**
     * Mostra o ecrã e espera que a tarifa seja gravada ou que o
     * administrador desista.
     *
     * @param owner a janela principal
     * @param desk o balcão, com um administrador autenticado
     * @return {@code true} se a tarifa mudou, para a janela actualizar a
     *         barra de estado
     */
    public static boolean ask(Window owner, ParkingDesk desk) {
        TariffDialog screen = new TariffDialog(owner, desk);
        screen.dialog.setVisible(true);
        return screen.done;
    }

    private void submit() {
        long hourlyCents = cents(hourly.getText());
        long capCents = cents(dailyCap.getText());
        if (hourlyCents < 0 || capCents < 0) {
            message.setText(Texts.get("tariff.error.money"));
            (hourlyCents < 0 ? hourly : dailyCap).requestFocusInWindow();
            return;
        }
        if (capCents < hourlyCents) {
            message.setText(Texts.get("tariff.error.cap"));
            dailyCap.requestFocusInWindow();
            return;
        }
        Tariff tariff = new Tariff((Integer) freeMinutes.getValue(), (Integer) fractionMinutes.getValue(),
                hourlyCents, capCents);
        if (sameAs(tariff, active)) {
            message.setText(Texts.get("tariff.error.same"));
            return;
        }

        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            desk.changeTariff(tariff, LocalDateTime.now());
        } catch (SQLException e) {
            Errors.saveFailed(dialog, e);
            return;
        } finally {
            dialog.setCursor(Cursor.getDefaultCursor());
        }
        done = true;
        dialog.dispose();
        JOptionPane.showMessageDialog(dialog.getOwner(),
                Texts.format("tariff.done", Texts.money(hourlyCents), Texts.money(capCents)),
                Texts.get("tariff.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Reescreve a tabela de exemplos: cada estadia com a tarifa actual e
     * com a que está escrita. Enquanto um valor estiver mal escrito, a
     * coluna da nova mostra um traço.
     */
    private void updateExamples() {
        Tariff typed = typedTariff();
        StringBuilder html = new StringBuilder(Texts.get("tariff.examples.head"));
        for (int minutes : EXAMPLES) {
            String now = Texts.money(desk.priceWith(active, minutes));
            String next = typed == null ? "—" : Texts.money(desk.priceWith(typed, minutes));
            html.append(Texts.format("tariff.examples.row", Texts.duration(minutes), now, next));
        }
        html.append(Texts.get("tariff.examples.tail"));
        examples.setText(html.toString());
        message.setText(" ");
    }

    /** @return a tarifa que está escrita, ou {@code null} se algum valor não servir */
    private Tariff typedTariff() {
        long hourlyCents = cents(hourly.getText());
        long capCents = cents(dailyCap.getText());
        if (hourlyCents < 0 || capCents < hourlyCents) {
            return null;
        }
        return new Tariff((Integer) freeMinutes.getValue(), (Integer) fractionMinutes.getValue(),
                hourlyCents, capCents);
    }

    private static boolean sameAs(Tariff a, Tariff b) {
        return a.getFreeMinutes() == b.getFreeMinutes()
                && a.getFractionMinutes() == b.getFractionMinutes()
                && a.getHourlyRateCents() == b.getHourlyRateCents()
                && a.getDailyCapCents() == b.getDailyCapCents();
    }

    /** Centavos para o campo de texto: 6050 dá "60,50". */
    private static String meticais(long cents) {
        return (cents / 100) + "," + String.format("%02d", cents % 100);
    }

    /**
     * Lê um valor em meticais, com vírgula ou ponto e no máximo dois
     * algarismos depois: "60", "60,5" e "60.50" servem.
     *
     * @param text o que está escrito no campo
     * @return o valor em centavos, ou -1 se não for um valor válido
     */
    private static long cents(String text) {
        String value = text.strip().replace(',', '.');
        int dot = value.indexOf('.');
        String whole = dot < 0 ? value : value.substring(0, dot);
        String fraction = dot < 0 ? "" : value.substring(dot + 1);
        // Até 9 algarismos inteiros: chega para qualquer preço e não transborda o long.
        if (whole.isEmpty() || whole.length() > 9 || fraction.length() > 2
                || !digitsOnly(whole) || !digitsOnly(fraction)) {
            return -1;
        }
        long result = Long.parseLong(whole) * 100;
        if (fraction.length() == 1) {
            result += (fraction.charAt(0) - '0') * 10;
        } else if (fraction.length() == 2) {
            result += Integer.parseInt(fraction);
        }
        return result;
    }

    private static boolean digitsOnly(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
