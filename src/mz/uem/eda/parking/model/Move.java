package mz.uem.eda.parking.model;

/**
 * Um movimento de um carro: de onde sai, para onde vai e em que ordem. É a
 * unidade que a interface reproduz, um por tique do temporizador (ADR-014).
 *
 * <p>Usa a matrícula e os códigos das baias, não {@link Vehicle} nem
 * {@link Lane}: a interface recebe movimentos e não pode, a partir deles,
 * chegar às pilhas ou filas do parque.
 *
 * <p>Três formas, conforme as pontas:
 * <ul>
 *   <li><b>Entrada</b>: sem origem ({@code from} é {@code null}), o carro
 *       entra no parque para a baia {@code to};</li>
 *   <li><b>Saída</b>: sem destino ({@code to} é {@code null}), o carro sai
 *       do parque a partir da baia {@code from};</li>
 *   <li><b>Manobra</b>: as duas pontas preenchidas, o carro passa de uma
 *       baia para outra (normalmente de ou para a baia de manobra). São as
 *       manobras que se contam: 2k para tirar um carro à profundidade k.</li>
 * </ul>
 *
 * <p>Imutável.
 */
public class Move {

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
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a posição no plano, a começar em 1 */
    public int getSequence() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return a matrícula normalizada do carro */
    public String getPlate() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o código da baia de origem, ou {@code null} numa entrada */
    public String getFromLaneCode() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return o código da baia de destino, ou {@code null} numa saída */
    public String getToLaneCode() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return {@code true} se o carro estiver a entrar no parque */
    public boolean isEntry() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return {@code true} se o carro estiver a sair do parque */
    public boolean isExit() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** @return {@code true} se o carro passar de uma baia para outra */
    public boolean isManoeuvre() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Dois movimentos são iguais se tiverem a mesma ordem, matrícula, origem
     * e destino. Serve sobretudo para os testes compararem um plano com o
     * esperado.
     */
    @Override
    public boolean equals(Object other) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Coerente com {@link #equals(Object)}: calculado a partir dos quatro campos. */
    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * @return o movimento numa linha, por exemplo {@code "3: AAA123MC L03 -> M"},
     *         com {@code IN} e {@code OUT} no lugar das pontas vazias; é o
     *         formato para imprimir um plano na consola
     */
    @Override
    public String toString() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
