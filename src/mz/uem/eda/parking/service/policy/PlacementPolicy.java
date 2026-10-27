package mz.uem.eda.parking.service.policy;

import java.time.LocalDateTime;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.StayClass;

/**
 * A política de colocação: em que baia fica o carro que entra.
 *
 * <p>Há dois métodos. O {@code EntryService} chama sempre o segundo, que
 * recebe também a hora a que o carro diz que sai (a entrada mais a duração
 * declarada, ADR-004). Por omissão, esse segundo método ignora a hora e
 * chama o primeiro: assim as políticas que só precisam da classe
 * ({@link FirstWithSpace}, {@link LeastOccupied}, {@link ByStayClass})
 * implementam só o primeiro, e só a {@link ByDeparture}, que precisa da
 * hora, implementa o segundo.
 */
public interface PlacementPolicy {
    /**
     * Escolhe a baia para um carro da classe de permanência indicada.
     * Percorre as baias só para leitura e ignora a baia de manobra.
     *
     * @param lanes as baias do parque, pela ordem da configuração
     * @param stayClass a classe de permanência do carro que chega
     * @return a baia escolhida, ou {@code null} se nenhuma o puder receber
     *         (o carro vai então para a fila de espera)
     * @complexity O(B), em que B é o número de baias (22 no parque real)
     */
    Lane chooseLane(Iterable<Lane> lanes, StayClass stayClass);

    /**
     * Escolhe a baia sabendo também a hora a que o carro diz que sai. Por
     * omissão ignora a hora e faz o mesmo que
     * {@link #chooseLane(Iterable, StayClass)}.
     *
     * @param lanes as baias do parque, pela ordem da configuração
     * @param stayClass a classe de permanência do carro que chega
     * @param expectedExit a hora de saída declarada: entrada mais a duração
     *        estimada
     * @return a baia escolhida, ou {@code null} se nenhuma o puder receber
     * @complexity O(B)
     */
    default Lane chooseLane(Iterable<Lane> lanes, StayClass stayClass, LocalDateTime expectedExit) {
        return chooseLane(lanes, stayClass);
    }
}
