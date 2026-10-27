package mz.uem.eda.parking.service.parking;

import java.util.Objects;
import java.time.LocalDateTime;

import mz.uem.eda.parking.model.Lane;
import mz.uem.eda.parking.model.ParkingSession;
import mz.uem.eda.parking.model.StayClass;
import mz.uem.eda.parking.model.Vehicle;
import mz.uem.eda.parking.service.policy.PlacementPolicy;


public class EntryService {

    public static final int SHORT_MAX_MINUTES = 2 * 60;
    public static final int FULL_DAY_MINUTES =  24 * 60;

    private final Iterable<Lane> lanes;
    private final PlacementPolicy policy;
    private int nextTicketNumber;

    public EntryService(Iterable<Lane> lanes, PlacementPolicy policy) {
        this.lanes = Objects.requireNonNull(lanes, "lanes não podem ser null");
        this.policy = Objects.requireNonNull(policy, "policy não pode ser null");
        this.nextTicketNumber = 1;
    }

     /** ADR-004: até 2 h curta duração, até 24 h jornada completa, depois permanência longa. */
    public static StayClass classify(int estimatedMinutes) {
        if(estimatedMinutes <= 0) {
            throw new IllegalArgumentException("estimatedMinutes deve ser positivo: " + estimatedMinutes);
        }
        if (estimatedMinutes <= SHORT_MAX_MINUTES) {
            return StayClass.SHORT;
        }
        if(estimatedMinutes <= FULL_DAY_MINUTES) {
            return StayClass.FULL_DAY;
        }
        return StayClass.LONG_TERM;

    }

    public ParkingSession enter(Vehicle vehicle, int estimatedMinutes, LocalDateTime entryTime){
        Objects.requireNonNull(vehicle, "vehicle não pode ser null");
        Objects.requireNonNull(entryTime, "entryTime não pode ser null");
        StayClass stayClass =  classify(estimatedMinutes);
        if (isParked(vehicle)) {
            throw new IllegalStateException("veículo já parqueado: " + vehicle.getPlate());
        }

        // A política recebe também a hora de saída declarada; só a ByDeparture a usa.
        Lane lane = policy.chooseLane(lanes, stayClass, entryTime.plusMinutes(estimatedMinutes));
        if (lane == null) {
            return null;
        }
        ParkingSession session = new ParkingSession(nextTicketNumber, vehicle, lane.getCode(),
                                                    stayClass, estimatedMinutes, entryTime);
        lane.asStack().push(session);
        nextTicketNumber++;
        return session;
    }

     /** O(n): percorre as baias só para leitura (ADR-009). */
     public boolean isParked(Vehicle vehicle) {
        for (Lane lane : lanes) {
            for (ParkingSession session : lane.contents()) {
                if (session.getVehicle().equals(vehicle)){
                    return true;
                }
            }
        }
        return  false;
     }

     public int getNextTicketNumber() {
        return nextTicketNumber;
    }

    /**
     * Repõe o número do próximo talão depois de reabrir o programa, para a
     * numeração continuar sem repetir talões já gravados. Só a camada
     * {@code persistence} (no arranque) chama este método.
     *
     * @param next o número gravado na última entrada, pelo menos 1
     * @throws IllegalArgumentException se {@code next} for menor que 1
     */
    public void restoreNextTicketNumber(int next) {
        if (next < 1) {
            throw new IllegalArgumentException("next deve ser pelo menos 1: " + next);
        }
        this.nextTicketNumber = next;
    }
}
