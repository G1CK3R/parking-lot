package mz.uem.eda.parking.service.views;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.service.ParkingDesk;

/**
 * Uma baia vista de fora, só para leitura. É o que a interface recebe para
 * desenhar o parque: tem o código, a capacidade, a classe e os carros pela
 * ordem em que estão, mas não tem {@code push} nem {@code pop}.
 *
 * <p>Existe porque a {@code ui} não pode importar {@link Lane}: a
 * {@code Lane} dá acesso à pilha ({@link Lane#asStack()}), e a regra do
 * projecto é que a interface nunca toca numa pilha nem numa fila (ADR-020).
 * Só a {@link ParkingDesk} cria vistas. O construtor é público só porque
 * as vistas vivem no pacote {@code service.views}, separado do balcão.
 *
 * <p>Não é uma cópia: lê a baia verdadeira, por isso mostra sempre o estado
 * actual. Criar uma vista não custa nada além de guardar a referência.
 */
public class LaneView {

    private final Lane lane;

    /**
     * @param lane a baia a mostrar
     */
    public LaneView(Lane lane) {
        this.lane = lane;
    }

    /** @return o código da baia, por exemplo {@code "J04"} */
    public String getCode() {
        return lane.getCode();
    }

    /** @return quantos carros cabem na baia */
    public int getCapacity() {
        return lane.getCapacity();
    }

    /**
     * @return a classe de permanência a que a baia se destina, ou
     *         {@code null} na baia de manobra
     */
    public StayClass getStayClass() {
        return lane.getStayClass();
    }

    /** @return {@code true} se esta for a baia de manobra */
    public boolean isBuffer() {
        return lane.isBuffer();
    }

    /**
     * @return quantos carros estão na baia
     * @complexity O(1)
     */
    public int size() {
        return lane.size();
    }

    /**
     * @return quantos carros ainda cabem
     * @complexity O(1)
     */
    public int freeSpace() {
        return lane.freeSpace();
    }

    /**
     * Os carros da baia, do topo (junto à saída) para o fundo. É a ordem em
     * que sairiam sem manobras.
     *
     * @return as sessões, do topo para o fundo
     * @complexity O(1) para obter; O(n) para percorrer
     */
    public Iterable<ParkingSession> sessions() {
        return lane.contents();
    }

    /** @return o código e a ocupação, por exemplo {@code "J04 3/10"} */
    @Override
    public String toString() {
        return lane.toString();
    }
}