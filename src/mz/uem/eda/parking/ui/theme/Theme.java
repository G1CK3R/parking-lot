package mz.uem.eda.parking.ui.theme;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;

import com.formdev.flatlaf.FlatLightLaf;

import mz.uem.eda.parking.model.StayClass;

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

    // ------------------------------------------------------------------
    // O parque visto de cima (ParkPanel)
    // ------------------------------------------------------------------

    /** O muro à volta do parque, que é também o fundo do painel. */
    public static final Color LOT_WALL = new Color(0x2B2E33);

    /** O asfalto do corredor. */
    public static final Color ASPHALT = new Color(0x43474D);

    /** O piso das baias, um pouco mais claro que o corredor. */
    public static final Color LANE_FLOOR = new Color(0x51565C);

    /** As linhas brancas pintadas no chão. */
    public static final Color PAINT = new Color(0xE8EAED);

    /** A linha amarela tracejada ao meio do corredor. */
    public static final Color CENTER_LINE = new Color(0xF2C94C);

    /** Os canteiros que ocupam o fundo das baias mais curtas. */
    public static final Color PLANTER = new Color(0x5E7F45);

    /** Os arbustos desenhados nos canteiros. */
    public static final Color BUSH = new Color(0x48663A);

    /** Os pneus dos carros. */
    public static final Color TYRE = new Color(0x18191B);

    /** Os vidros dos carros. */
    public static final Color GLASS = new Color(0x22313F);

    /** As riscas amarelas da baia de manobra. */
    public static final Color HAZARD = new Color(0xF2C230);

    /** O contorno de uma baia cheia. */
    public static final Color FULL = new Color(0xE5534B);

    /** Curta duração: placa da baia e lugar pintado à volta do carro. */
    public static final Color SHORT_STAY = new Color(0xE9A23B);

    /** Jornada completa: placa da baia e lugar pintado à volta do carro. */
    public static final Color FULL_DAY_STAY = new Color(0x3D8BD9);

    /** Permanência longa: placa da baia e lugar pintado à volta do carro. */
    public static final Color LONG_TERM_STAY = new Color(0x9A6ACB);

    /**
     * As cores de pintura dos carros, as mais comuns na estrada: branco,
     * prata, preto, cinzento, vermelho, azul, verde e bege. A cor de cada
     * carro sai da matrícula, por isso o mesmo carro tem sempre a mesma.
     */
    private static final Color[] CAR_PAINTS = {
        new Color(0xF1F3F4), new Color(0xB9BEC4), new Color(0x2A2C30), new Color(0x6B7177),
        new Color(0xB3261E), new Color(0x1F4E8C), new Color(0x2E5E4E), new Color(0xCDB891),
    };

    private Theme() {
    }

    /**
     * @param plate a matrícula do carro
     * @return a cor de pintura do carro, sempre a mesma para a mesma
     *         matrícula
     */
    public static Color paintOf(String plate) {
        // Soma das letras, cada uma pesada pela posição, para matrículas
        // parecidas não darem sempre a mesma cor.
        int sum = 0;
        for (int i = 0; i < plate.length(); i++) {
            sum += plate.charAt(i) * (i + 1);
        }
        return CAR_PAINTS[sum % CAR_PAINTS.length];
    }

    /**
     * @param stayClass a classe de permanência
     * @return a cor dessa classe, para a placa da baia e o lugar do carro
     */
    public static Color colorOf(StayClass stayClass) {
        switch (stayClass) {
            case SHORT:
                return SHORT_STAY;
            case LONG_TERM:
                return LONG_TERM_STAY;
            default:
                return FULL_DAY_STAY;
        }
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