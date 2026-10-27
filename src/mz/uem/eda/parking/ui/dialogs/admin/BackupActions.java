package mz.uem.eda.parking.ui.dialogs.admin;

import java.awt.Cursor;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import mz.uem.eda.parking.service.ParkingDesk;
import mz.uem.eda.parking.ui.Texts;

/**
 * A cópia de segurança e o restauro (RF24), só para o administrador. Não
 * são ecrãs: escolhe-se o ficheiro no diálogo do sistema e o resultado
 * aparece numa caixa de mensagem.
 *
 * <p>A cópia é um ficheiro de texto do próprio programa, com todas as
 * tabelas; quem a escreve e lê é a {@code ParkingDesk}. Restaurar substitui
 * tudo o que está na base pelo conteúdo da cópia, numa só transacção; como
 * o que está em memória deixa de corresponder à base, o programa fecha
 * depois e tem de ser aberto de novo.
 */
public final class BackupActions {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");

    private BackupActions() {
    }

    /**
     * Pede o ficheiro e grava a cópia de segurança.
     *
     * @param owner a janela principal
     * @param desk o balcão, com um administrador autenticado
     */
    public static void backup(Window owner, ParkingDesk desk) {
        LocalDateTime now = LocalDateTime.now();
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(Texts.format("backup.file", now.format(STAMP))));
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        int rows;
        try {
            rows = desk.backup(file.toPath(), now);
        } catch (IOException | SQLException e) {
            JOptionPane.showMessageDialog(owner, Texts.format("backup.failed", e.getMessage()),
                    Texts.get("backup.title"), JOptionPane.ERROR_MESSAGE);
            return;
        } finally {
            owner.setCursor(Cursor.getDefaultCursor());
        }
        JOptionPane.showMessageDialog(owner,
                Texts.format("backup.done", String.valueOf(rows), file.getAbsolutePath()),
                Texts.get("backup.title"), JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Pede o ficheiro, confirma e restaura a cópia de segurança.
     *
     * @param owner a janela principal
     * @param desk o balcão, com um administrador autenticado
     * @return {@code true} se a base foi restaurada; a janela principal
     *         deve então fechar o programa
     */
    public static boolean restore(Window owner, ParkingDesk desk) {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        File file = chooser.getSelectedFile();
        Object[] options = { Texts.get("restore.confirm"), Texts.get("dialog.cancel") };
        int answer = JOptionPane.showOptionDialog(owner,
                Texts.format("restore.question", file.getAbsolutePath()), Texts.get("restore.title"),
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        if (answer != 0) {
            return false;
        }
        owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        int rows;
        try {
            rows = desk.restore(file.toPath(), LocalDateTime.now());
        } catch (IOException | SQLException e) {
            JOptionPane.showMessageDialog(owner, Texts.format("restore.failed", e.getMessage()),
                    Texts.get("restore.title"), JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            owner.setCursor(Cursor.getDefaultCursor());
        }
        JOptionPane.showMessageDialog(owner, Texts.format("restore.done", String.valueOf(rows)),
                Texts.get("restore.title"), JOptionPane.INFORMATION_MESSAGE);
        return true;
    }
}
