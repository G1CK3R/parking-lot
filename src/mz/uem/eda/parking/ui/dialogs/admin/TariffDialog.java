package mz.uem.eda.parking.ui.dialogs.admin;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
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
import mz.uem.eda.parking.ui.dialogs.common.Errors;
import mz.uem.eda.parking.ui.dialogs.common.FormPanel;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * Muda a tarifa (RF13, RF16, RF17), só para o administrador. Os valores
 * começam com os da tarifa em vigor e estão em duas colunas:
 * <ul>
 * <li><b>Estadia</b>: tolerância gratuita, fracção mínima de cobrança,
 * valor por hora e tecto diário;</li>
 * <li><b>Noite, fim-de-semana, multa e avença</b>: valor por hora das 20h às
 * 7h, desconto ao sábado e ao domingo, multa por talão perdido e valor da
 * avença mensal.</li>
 * </ul>
 *
 * <p>Por baixo, uma tabela de exemplos mostra quanto pagam algumas estadias
 * com a tarifa actual e com a nova, e muda enquanto se escreve. Cada
 * exemplo começa num dia e numa hora certos (uma terça de manhã, uma terça
 * à noite, um sábado), para se ver o efeito da noite e do fim-de-semana. As
 * contas são da {@link ParkingDesk#priceWith}, com as mesmas regras da
 * cobrança verdadeira; o ecrã não calcula preços.
 *
 * <p>Os valores escrevem-se em meticais ("60" ou "60,50") e guardam-se em
 * centavos, num {@code long}, como em todo o programa (ver {@link Tariff}).
 *
 * <p>A tarifa nova vale para todas as saídas a partir de agora, incluindo
 * as dos carros que já estão no parque. Os pagamentos antigos continuam
 * ligados à tarifa com que foram calculados (ADR-031).
 */
public final class TariffDialog {

    /** Os exemplos: dia da semana, hora de entrada e duração em minutos. */
    private static final DayOfWeek[] EXAMPLE_DAYS = { DayOfWeek.TUESDAY, DayOfWeek.TUESDAY, DayOfWeek.TUESDAY,
        DayOfWeek.TUESDAY, DayOfWeek.SATURDAY, DayOfWeek.TUESDAY, DayOfWeek.TUESDAY };
    private static final int[] EXAMPLE_HOURS = { 10, 10, 10, 21, 10, 8, 10 };
    private static final int[] EXAMPLE_MINUTES = { 10, 45, 180, 180, 180, 600, 1560 };

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final Tariff active;
    private final JSpinner freeMinutes;
    private final JSpinner fractionMinutes;
    private final JTextField hourly = new JTextField(10);
    private final JTextField dailyCap = new JTextField(10);
    private final JTextField nightRate = new JTextField(10);
    private final JSpinner weekendDiscount;
    private final JTextField lostTicket = new JTextField(10);
    private final JTextField monthlyPass = new JTextField(10);
    private final JLabel examples = new JLabel();
    private final JLabel message = new JLabel();
    private boolean done;

    private TariffDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.active = desk.getTariff();
        this.dialog = new JDialog(owner, Texts.get("tariff.title"), Dialog.ModalityType.APPLICATION_MODAL);

        freeMinutes = new JSpinner(new SpinnerNumberModel(active.getFreeMinutes(), 0, 240, 5));
        fractionMinutes = new JSpinner(new SpinnerNumberModel(active.getFractionMinutes(), 1, 60, 1));
        weekendDiscount = new JSpinner(new SpinnerNumberModel(active.getWeekendDiscountPercent(), 0, 100, 5));
        hourly.setText(Texts.amount(active.getHourlyRateCents()));
        dailyCap.setText(Texts.amount(active.getDailyCapCents()));
        nightRate.setText(Texts.amount(active.getNightRateCents()));
        lostTicket.setText(Texts.amount(active.getLostTicketCents()));
        monthlyPass.setText(Texts.amount(active.getMonthlyPassCents()));

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
        ((JSpinner.DefaultEditor) weekendDiscount.getEditor()).getTextField().addActionListener(save);
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
        weekendDiscount.addChangeListener(spinnerChanged);
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
        for (JTextField field : moneyFields()) {
            field.getDocument().addDocumentListener(textChanged);
        }

        FormPanel base = new FormPanel();
        base.getPanel().setBorder(null);
        base.addText(Texts.get("tariff.section.base"));
        base.addField(Texts.get("tariff.free"), freeMinutes);
        base.addField(Texts.get("tariff.fraction"), fractionMinutes);
        base.addField(Texts.get("tariff.hourly"), hourly);
        base.addField(Texts.get("tariff.cap"), dailyCap);

        FormPanel periods = new FormPanel();
        periods.getPanel().setBorder(null);
        periods.addText(Texts.get("tariff.section.periods"));
        periods.addField(Texts.get("tariff.night"), nightRate);
        periods.addField(Texts.get("tariff.weekend"), weekendDiscount);
        periods.addField(Texts.get("tariff.lost"), lostTicket);
        periods.addField(Texts.get("tariff.pass"), monthlyPass);

        JPanel columns = new JPanel(new GridLayout(1, 2, Theme.PADDING * 2, 0));
        columns.add(base.getPanel());
        columns.add(periods.getPanel());

        FormPanel bottom = new FormPanel();
        bottom.getPanel().setBorder(null);
        bottom.addField(Texts.get("tariff.examples"), examples);
        bottom.addMessage(message);
        bottom.addButtons(cancel, submit);

        JPanel content = new JPanel(new BorderLayout(0, Theme.PADDING));
        content.setBorder(Theme.padding());
        content.add(new JLabel(Texts.get("tariff.intro")), BorderLayout.NORTH);
        content.add(columns, BorderLayout.CENTER);
        content.add(bottom.getPanel(), BorderLayout.SOUTH);

        dialog.setContentPane(content);
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

    /** Os campos com valores em meticais, pela ordem do ecrã. */
    private JTextField[] moneyFields() {
        return new JTextField[] { hourly, dailyCap, nightRate, lostTicket, monthlyPass };
    }

    private void submit() {
        for (JTextField field : moneyFields()) {
            if (cents(field.getText()) < 0) {
                message.setText(Texts.get("tariff.error.money"));
                field.requestFocusInWindow();
                return;
            }
        }
        if (cents(dailyCap.getText()) < cents(hourly.getText())) {
            message.setText(Texts.get("tariff.error.cap"));
            dailyCap.requestFocusInWindow();
            return;
        }
        Tariff tariff = typedTariff();
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
                Texts.format("tariff.done", Texts.money(tariff.getHourlyRateCents()),
                        Texts.money(tariff.getDailyCapCents())),
                Texts.get("tariff.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Reescreve a tabela de exemplos: cada estadia com a tarifa actual e
     * com a que está escrita, e no fim a multa e a avença. Enquanto um valor
     * estiver mal escrito, a coluna da nova mostra um traço.
     */
    private void updateExamples() {
        Tariff typed = typedTariff();
        LocalDate today = LocalDate.now();
        StringBuilder html = new StringBuilder(Texts.get("tariff.examples.head"));
        for (int i = 0; i < EXAMPLE_MINUTES.length; i++) {
            LocalDate day = today.with(TemporalAdjusters.nextOrSame(EXAMPLE_DAYS[i]));
            LocalDateTime entry = day.atTime(EXAMPLE_HOURS[i], 0);
            int minutes = EXAMPLE_MINUTES[i];
            String label = Texts.format("tariff.example.stay", Texts.duration(minutes),
                    Texts.get("day." + EXAMPLE_DAYS[i].name()), String.valueOf(EXAMPLE_HOURS[i]), "00");
            String now = Texts.money(desk.priceWith(active, entry, minutes));
            String next = typed == null ? "—" : Texts.money(desk.priceWith(typed, entry, minutes));
            html.append(Texts.format("tariff.examples.row", label, now, next));
        }
        html.append(Texts.format("tariff.examples.row", Texts.get("tariff.example.lost"),
                Texts.money(active.getLostTicketCents()),
                typed == null ? "—" : Texts.money(typed.getLostTicketCents())));
        html.append(Texts.format("tariff.examples.row", Texts.get("tariff.example.pass"),
                Texts.money(active.getMonthlyPassCents()),
                typed == null ? "—" : Texts.money(typed.getMonthlyPassCents())));
        html.append(Texts.get("tariff.examples.tail"));
        examples.setText(html.toString());
        message.setText(" ");
    }

    /** @return a tarifa que está escrita, ou {@code null} se algum valor não servir */
    private Tariff typedTariff() {
        long hourlyCents = cents(hourly.getText());
        long capCents = cents(dailyCap.getText());
        long nightCents = cents(nightRate.getText());
        long lostCents = cents(lostTicket.getText());
        long passCents = cents(monthlyPass.getText());
        if (hourlyCents < 0 || capCents < hourlyCents || nightCents < 0 || lostCents < 0 || passCents < 0) {
            return null;
        }
        return new Tariff((Integer) freeMinutes.getValue(), (Integer) fractionMinutes.getValue(),
                hourlyCents, capCents, nightCents, (Integer) weekendDiscount.getValue(), lostCents, passCents);
    }

    private static boolean sameAs(Tariff a, Tariff b) {
        return a.getFreeMinutes() == b.getFreeMinutes()
                && a.getFractionMinutes() == b.getFractionMinutes()
                && a.getHourlyRateCents() == b.getHourlyRateCents()
                && a.getDailyCapCents() == b.getDailyCapCents()
                && a.getNightRateCents() == b.getNightRateCents()
                && a.getWeekendDiscountPercent() == b.getWeekendDiscountPercent()
                && a.getLostTicketCents() == b.getLostTicketCents()
                && a.getMonthlyPassCents() == b.getMonthlyPassCents();
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
