package mz.uem.eda.parking.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class Reservation {
    public enum Status {
        /** À espera do carro. */
        ACTIVE,
        /** O carro chegou e a reserva deu lugar a uma sessão. */
        CHECKED_IN,
        /** A tolerância acabou antes de o carro chegar. */
        EXPIRED
    }

    private final String plate;
    private final StayClass stayClass;
    private final LocalDateTime createdAt;
    private final LocalDateTime expiresAt;
    private Status status;

    public Reservation(String plate, StayClass stayClass, LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        Objects.requireNonNull(stayClass, "stayClass não pode ser null");
        Objects.requireNonNull(createdAt, "createdAt não pode ser null");
        Objects.requireNonNull(expiresAt, "expiresAt não pode ser null");
        String normalized = Vehicle.normalizePlate(plate);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("plate não pode estár vazia: '" + plate + "'");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("expiresAt tem de ser depois de expiredAt: " + expiresAt);
        }
        this.plate = normalized;
        this.stayClass = stayClass;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = Status.ACTIVE;
    }
}
