package mz.uem.eda.parking.ui.dialogs.admin;

import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.time.LocalDateTime;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import mz.uem.eda.parking.model.ParkPolicies;
import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;
import mz.uem.eda.parking.ui.dialogs.common.Errors;
import mz.uem.eda.parking.ui.dialogs.common.FormPanel;
import mz.uem.eda.parking.ui.theme.Theme;

/**
 * Muda as políticas do parque, só para o administrador:
 * <ul>
 * <li><b>Colocação</b>: em que baia fica o carro que entra (primeira com
 * espaço, menos ocupada, por classe de permanência ou por hora de
 * saída);</li>
 * <li><b>Atendimento</b>: de qual fila de espera sai o próximo carro
 * (prioridade estrita ou rotação), e, na rotação, quantos preferenciais
 * seguidos antes de um normal.</li>
 * </ul>
 *
 * <p>Cada opção tem por baixo uma frase que diz o que faz, para o
 * administrador não ter de saber o nome das classes. O ecrã começa com as
 * políticas em vigor marcadas.
 *
 * <p>A escolha passa à {@link ParkingDesk#changePolicies}, que a grava e a
 * aplica logo: não é preciso reabrir o programa. O ecrã não conhece as
 * classes das políticas, só os nomes de {@link ParkPolicies} (ADR-032).
 */
public final class PoliciesDialog {

    /** Quanto as frases de explicação ficam recolhidas, para alinharem com o texto da opção. */
    private static final int HINT_INDENT = 21;

    private final JDialog dialog;
    private final ParkingDesk desk;
    private final ParkPolicies active;
    private final JRadioButton[] placements;
    private final JRadioButton[] services;
    private final JSpinner turns;
    private final JLabel message = new JLabel();
    private boolean done;

    private PoliciesDialog(Window owner, ParkingDesk desk) {
        this.desk = desk;
        this.active = desk.getPolicies();
        this.dialog = new JDialog(owner, Texts.get("policies.title"), Dialog.ModalityType.APPLICATION_MODAL);

        ParkPolicies.Placement[] placementNames = ParkPolicies.Placement.values();
        placements = new JRadioButton[placementNames.length];
        ButtonGroup placementGroup = new ButtonGroup();
        for (int i = 0; i < placementNames.length; i++) {
            placements[i] = new JRadioButton(Texts.get("policies.placement." + placementNames[i].name()));
            placements[i].setSelected(placementNames[i] == active.getPlacement());
            placementGroup.add(placements[i]);
        }

        ParkPolicies.Service[] serviceNames = ParkPolicies.Service.values();
        services = new JRadioButton[serviceNames.length];
        ButtonGroup serviceGroup = new ButtonGroup();
        for (int i = 0; i < serviceNames.length; i++) {
            String label = serviceNames[i] == ParkPolicies.Service.ROUND_ROBIN
                    ? Texts.get("policies.option.ROUND_ROBIN")
                    : Texts.get("policies.service." + serviceNames[i].name());
            services[i] = new JRadioButton(label);
            services[i].setSelected(serviceNames[i] == active.getService());
            serviceGroup.add(services[i]);
        }

        turns = new JSpinner(new SpinnerNumberModel(active.getPreferentialTurns(),
                ParkPolicies.MIN_PREFERENTIAL_TURNS, ParkPolicies.MAX_PREFERENTIAL_TURNS, 1));
        // O k só conta na rotação: com a prioridade estrita fica desligado.
        ActionListener serviceChanged = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateTurns();
                message.setText(" ");
            }
        };
        for (JRadioButton service : services) {
            service.addActionListener(serviceChanged);
        }
        ActionListener placementChanged = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                message.setText(" ");
            }
        };
        for (JRadioButton placement : placements) {
            placement.addActionListener(placementChanged);
        }

        JButton submit = new JButton(Texts.get("policies.submit"));
        submit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submit();
            }
        });
        JButton cancel = new JButton(Texts.get("dialog.cancel"));
        cancel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dialog.dispose();
            }
        });

        FormPanel form = new FormPanel();
        form.addText(Texts.get("policies.intro"));
        form.addText(Texts.get("policies.section.placement"));
        for (int i = 0; i < placementNames.length; i++) {
            form.addComponent(option(placements[i], Texts.get("policies.hint." + placementNames[i].name())));
        }
        form.addText(Texts.get("policies.section.service"));
        for (int i = 0; i < serviceNames.length; i++) {
            form.addComponent(option(services[i], Texts.get("policies.hint." + serviceNames[i].name())));
        }
        form.addField(Texts.get("policies.turns"), turns);
        form.addComponent(muted(Texts.get("policies.compare")));
        form.addMessage(message);
        form.addButtons(cancel, submit);

        dialog.setContentPane(form.getPanel());
        dialog.getRootPane().setDefaultButton(submit);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        updateTurns();
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
    }

    /**
     * Mostra o ecrã e espera que as políticas sejam gravadas ou que o
     * administrador desista.
     *
     * @param owner a janela principal
     * @param desk o balcão, com um administrador autenticado
     * @return {@code true} se as políticas mudaram, para a janela
     *         actualizar a barra de estado
     */
    public static boolean ask(Window owner, ParkingDesk desk) {
        PoliciesDialog screen = new PoliciesDialog(owner, desk);
        screen.dialog.setVisible(true);
        return screen.done;
    }

    /**
     * O nome de uma política em português, como aparece na barra de estado
     * e na mensagem de confirmação: por exemplo "Por hora de saída" e
     * "Rotação 3:1".
     *
     * @param policies as políticas
     * @return {nome da colocação, nome do atendimento}
     */
    public static String[] names(ParkPolicies policies) {
        return new String[] {
            Texts.get("policies.placement." + policies.getPlacement().name()),
            Texts.format("policies.service." + policies.getService().name(),
                    String.valueOf(policies.getPreferentialTurns())) };
    }

    private void submit() {
        ParkPolicies chosen = chosen();
        if (chosen.sameAs(active)) {
            message.setText(Texts.get("policies.error.same"));
            return;
        }

        dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            desk.changePolicies(chosen, LocalDateTime.now());
        } catch (SQLException e) {
            Errors.saveFailed(dialog, e);
            return;
        } finally {
            dialog.setCursor(Cursor.getDefaultCursor());
        }
        done = true;
        dialog.dispose();
        String[] names = names(chosen);
        JOptionPane.showMessageDialog(dialog.getOwner(), Texts.format("policies.done", names[0], names[1]),
                Texts.get("policies.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    /** @return as políticas marcadas no ecrã */
    private ParkPolicies chosen() {
        ParkPolicies.Placement placement = active.getPlacement();
        ParkPolicies.Placement[] placementNames = ParkPolicies.Placement.values();
        for (int i = 0; i < placements.length; i++) {
            if (placements[i].isSelected()) {
                placement = placementNames[i];
            }
        }
        ParkPolicies.Service service = active.getService();
        ParkPolicies.Service[] serviceNames = ParkPolicies.Service.values();
        for (int i = 0; i < services.length; i++) {
            if (services[i].isSelected()) {
                service = serviceNames[i];
            }
        }
        return new ParkPolicies(placement, service, (Integer) turns.getValue());
    }

    /** Liga o campo do k só quando a rotação está marcada. */
    private void updateTurns() {
        turns.setEnabled(chosen().getService() == ParkPolicies.Service.ROUND_ROBIN);
    }

    /** Uma opção: o botão e, por baixo, a frase que explica o que faz. */
    private static JComponent option(JRadioButton button, String hint) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        button.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JLabel text = muted(hint);
        text.setBorder(BorderFactory.createEmptyBorder(0, HINT_INDENT, 0, 0));
        text.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        panel.add(button);
        panel.add(text);
        return panel;
    }

    private static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.MUTED);
        return label;
    }
}
