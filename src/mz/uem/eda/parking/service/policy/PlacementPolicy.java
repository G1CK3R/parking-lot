package mz.uem.eda.parking.service.policy;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.StayClass;

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
}
