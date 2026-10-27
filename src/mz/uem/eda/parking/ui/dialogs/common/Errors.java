package mz.uem.eda.parking.ui.dialogs.common;

import java.awt.Component;
import java.sql.SQLException;

import javax.swing.JOptionPane;

import mz.uem.eda.parking.ui.Texts;

/**
 * A mensagem que os diálogos mostram quando uma operação mudou o parque em
 * memória mas não ficou gravada na base de dados. O operador deve fechar e
 * voltar a abrir o programa, que repõe o último estado gravado.
 *
 * <p>É pública porque a janela principal também grava (terminar a sessão,
 * desfazer, admitir carros da fila de espera).
 */
public final class Errors {

    private Errors() {
    }

    /**
     * @param parent a janela por cima da qual aparece a mensagem
     * @param e o erro da base de dados
     */
    public static void saveFailed(Component parent, SQLException e) {
        JOptionPane.showMessageDialog(parent, Texts.format("error.save", e.getMessage()),
                Texts.get("error.save.title"), JOptionPane.ERROR_MESSAGE);
    }
}