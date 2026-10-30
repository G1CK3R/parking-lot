package mz.uem.eda.parking.service.parking;

import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.Vehicle;


/**
 * Um carro à espera de lugar: a viatura, a duração estimada que o condutor
 * indicou e a hora a que chegou. É o que fica nas filas de espera do
 * {@link WaitingQueueService} enquanto o parque está cheio.
 *
 * <p>Guarda a duração em minutos, e não a classe de permanência, porque é
 * isso que o {@link EntryService#enter} recebe quando o carro finalmente
 * entra. A hora de chegada serve para medir o tempo de espera de cada fila,
 * que é o que mostra a diferença entre as políticas de atendimento.
 *
 * <p>Imutável.
 */
public class WaitingCar {

    private final Vehicle vehicle;
    private final int estimatedMinutes;
    private final LocalDateTime arrivedAt;
    private final boolean preferential;

     /**
     * Cria o registo de um carro em espera.
     *
     * @param vehicle a viatura
     * @param estimatedMinutes a duração estimada, em minutos, maior que 0
     * @param arrivedAt a hora de chegada à fila
     * @param preferential {@code true} se o carro esperar na fila
     *        preferencial
     * @throws NullPointerException se {@code vehicle} ou {@code arrivedAt}
     *         for {@code null}
     * @throws IllegalArgumentException se a duração não for positiva*/


     public WaitingCar(Vehicle vehicle, int estimatedMinutes, LocalDateTime arrivedAt,
                        boolean preferential){
        Objects.requireNonNull(vehicle, "vehicle não pode ser null");
        Objects.requireNonNull(arrivedAt, "arrivedAt não pode ser null");
        if (estimatedMinutes <= 0) {
            throw new IllegalArgumentException("estimatedMinutes deve ser positivo: " + estimatedMinutes);
        }
        this.vehicle = vehicle;
        this.estimatedMinutes = estimatedMinutes;
        this.arrivedAt = arrivedAt;
        this.preferential = preferential;
     }

    /**
     * @return a viatura, nunca {@code null}
     * @complexity O(1)
     */
    public Vehicle getVehicle() {
        return vehicle;
    }

    /**
     * @return a duração estimada, em minutos
     * @complexity O(1)
     */
    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    /**
     * @return a hora a que o carro chegou à fila, nunca {@code null}
     * @complexity O(1)
     */
    public LocalDateTime getArrivedAt() {
        return arrivedAt;
    }

    /**
     * @return {@code true} se o carro esperar na fila preferencial
     * @complexity O(1)
     */
    public boolean isPreferential() {
        return preferential;
    }

    /**
     * @return a matrícula e a fila, por exemplo {@code "AAA123MC (P)"}
     * @complexity O(1)
     */
    @Override
    public String toString() {
        return vehicle.getPlate() + (preferential ? " (P)" : " (N)");
    }
}
