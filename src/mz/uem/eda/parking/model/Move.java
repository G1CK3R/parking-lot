package mz.uem.eda.parking.model;

import java.util.Objects;

/**
 * Um movimento de um carro: de onde sai, para onde vai e em que ordem. É a
 * unidade que a interface reproduz, um por tique do temporizador (ADR-014).
 *
 * <p>Usa a matrícula e os códigos das baias, não {@link Vehicle} nem
 * {@link Lane}: a interface recebe movimentos e não pode, a partir deles,
 * chegar às pilhas do parque.
 *
 * <p>Três formas, conforme as pontas:
 * <ul>
 *   <li><b>Entrada</b>: sem origem ({@code from} é {@code null}), o carro
 *       entra no parque para a baia {@code to};</li>
 *   <li><b>Saída</b>: sem destino ({@code to} é {@code null}), o carro sai
 *       do parque a partir da baia {@code from};</li>
 *   <li><b>Manobra</b>: as duas pontas preenchidas, o carro passa de uma
 *       baia para outra (de ou para a baia de manobra). São as manobras que
 *       se contam: 2k para tirar um carro à profundidade k.</li>
 * </ul>
 *
 * <p>Imutável.
 */
public class Move {

    private final int sequence;
    private final String plate;
    private final String fromLaneCode;
    private final String toLaneCode;

     /**
     * Cria um movimento.
     *
     * @param sequence a posição do movimento no seu plano, a começar em 1
     * @param plate a matrícula normalizada do carro, nunca {@code null} nem
     *        vazia
     * @param fromLaneCode o código da baia de origem, ou {@code null} numa
     *        entrada
     * @param toLaneCode o código da baia de destino, ou {@code null} numa
     *        saída
     * @throws NullPointerException se {@code plate} for {@code null}
     * @throws IllegalArgumentException se {@code sequence} for menor que 1,
     *         {@code plate} estiver vazia, as duas baias forem {@code null}
     *         ou origem e destino forem a mesma baia
     */

    public Move(int sequence, String plate, String fromLaneCode, String toLaneCode) {
        Objects.requireNonNull(plate, "A matricula não pdoe ser null");
        if (sequence < 1) {
            throw new IllegalArgumentException("A sequência deve ser ao menos 1: " + sequence);
        }
        if (plate.isBlank()) {
            throw new IllegalArgumentException("plate não pode estár em branco");
        }
        if (fromLaneCode == null && toLaneCode == null) {
            throw new IllegalArgumentException("um movimento precisa de ao menos uma baía");
        }
        if (fromLaneCode != null && fromLaneCode.equals(toLaneCode)) {
            throw new IllegalArgumentException("Alvo e destino são iguais: " + fromLaneCode);
        }
        this.sequence = sequence;
        this.plate = plate;
        this.fromLaneCode = fromLaneCode;
        this.toLaneCode = toLaneCode;
    }

     public int getSequence() {
         return sequence;
     }

     public String getPlate() {
         return plate;
     }

     public String getFromLaneCode() {
         return fromLaneCode;
     }

     public String getToLaneCode() {
         return toLaneCode;
     }

    /** @return {@code true} se o carro estiver a entrar no parque */
    public boolean isEntry() {
        return fromLaneCode == null;
    }

    /** @return {@code true} se o carro estiver a sair do parque */
    public boolean isExit() {
        return toLaneCode == null;
    }

    /** @return {@code true} se o carro passar de uma baia para outra */
    public boolean isManoeuvre() {
        return fromLaneCode != null && toLaneCode != null;
    }

    @Override 
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Move)) {
            return false;
        }
        Move that =  (Move) other;
        return sequence == that.sequence
                && plate.equals(that.plate)
                && Objects.equals(fromLaneCode, that.fromLaneCode)
                && Objects.equals(toLaneCode, that.toLaneCode);
    }

    /**
     * @return o movimento numa linha, por exemplo {@code "3: AAA123MC L03 -> M"},
     *         com {@code IN} e {@code OUT} no lugar das pontas vazias; é o
     *         formato para imprimir um plano na consola
     */
    @Override
    public String toString() {
        String from = fromLaneCode == null ? "IN" : fromLaneCode;
        String to = toLaneCode == null ? "OUT" : toLaneCode;
        return sequence + ": " + plate + " " + from + " -> " + to;
    }
}
