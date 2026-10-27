package mz.uem.eda.parking.ui.theme;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * O aspecto da interface num só sítio: o tema, as cores e os espaçamentos.
 * Os ecrãs usam estas constantes em vez de escreverem números soltos, para
 * todos ficarem iguais.
 *
 * <p>O tema é o FlatLaf, um Look and Feel: muda só a forma como os
 * componentes do Swing são desenhados, não o que fazem. Trocá-lo pelo
 * Nimbus é mudar uma linha em {@link #apply()}.
 */
public final class Theme {

    /** Espaço entre componentes vizinhos, em píxeis. */
    public static final int GAP = 8;

    /** Margem à volta de um painel ou diálogo, em píxeis. */
    public static final int PADDING = 16;

    /** Cor das mensagens de erro dentro dos diálogos. */
    public static final Color ERROR = new Color(0xB3261E);

    /** Cor dos textos secundários, como a barra de estado. */
    public static final Color MUTED = new Color(0x5F6368);

    private Theme() {
    }

    /**
     * Aplica o tema. Chama-se uma vez, antes de criar a primeira janela.
     */
    public static void apply() {
        FlatLightLaf.setup();
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 8);
    }

    /** @return uma margem vazia de {@link #PADDING} nos quatro lados */
    public static Border padding() {
        return BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING);
    }
}