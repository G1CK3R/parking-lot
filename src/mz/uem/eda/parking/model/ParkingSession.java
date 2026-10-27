package mz.uem.eda.parking.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class ParkingSession {
    public enum  Status{
        ACTIVE, 

        /*O carro  saiu e a estadia foi paga */
        CLOSED,
        /** A entrada foi desfeita pelo operador: o talão ficou anulado. */
        VOIDED
    }


    private final int ticketNumber;
    private final Vehicle vehicle;
    private final String laneCode;
    private final StayClass stayClass;
    private final int estimatedMinutes;
    private final LocalDateTime entryTime;

    private LocalDateTime exitTime;
    private int manoeuvres;
    private Status status;

    private long id;


    public ParkingSession(int ticketNumber, Vehicle vehicle, String laneCode,
        StayClass stayClass, int estimatedMinutes, LocalDateTime entryTime){
            Objects.requireNonNull(vehicle, "vehicle não pode ser null");
        Objects.requireNonNull(laneCode, "laneCode não pode ser null");
        Objects.requireNonNull(stayClass, "stayClass não pode ser null");
        Objects.requireNonNull(entryTime, "entryTime não pode ser null");

     if (ticketNumber <= 0) {
            throw new IllegalArgumentException("ticketNumber tem de ser positivo: " + ticketNumber);
        }
        if (laneCode.isBlank()) {
            throw new IllegalArgumentException("laneCode não pode estar em branco");
        }
        if (estimatedMinutes < 0) {
            throw new IllegalArgumentException("estimatedMinutes não pode ser negativo: " + estimatedMinutes);
        }

        this.ticketNumber = ticketNumber;
        this.vehicle = vehicle;
        this.laneCode = laneCode;
        this.stayClass = stayClass;
        this.estimatedMinutes = estimatedMinutes;
        this.entryTime = entryTime;
        this.status = Status.ACTIVE;
    }


    public void close(LocalDateTime exitTime, int manoeuvres) {
        Objects.requireNonNull(exitTime, "exitTime Não pode ser null");
         if (status == Status.CLOSED) {
            throw new IllegalStateException("A sessão já está fechada: #" + ticketNumber);
        }
        if (exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("exitTime não pode ser antes de entryTime: " + exitTime);
        }
        if (manoeuvres < 0) {
            throw new IllegalArgumentException("manoeuvres não pode ser negativa: " + manoeuvres);
        }
        this.exitTime = exitTime;
        this.manoeuvres = manoeuvres;
        this.status = Status.CLOSED;
    }

    /**
     * Anula a entrada: o operador desfez-a (por exemplo, a matrícula estava
     * errada). O carro sai sem pagar e o talão fica anulado.
     *
     * @throws IllegalStateException se a sessão já não estiver activa
     */
    public void voidEntry() {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("só se anula uma sessão activa: #" + ticketNumber);
        }
        status = Status.VOIDED;
    }

    public int getTicketNumber() {
        return ticketNumber;
    }

    /** @return a viatura, nunca {@code null} */
    public Vehicle getVehicle() {
        return vehicle;
    }

    /** @return o código da baia onde o carro está ou esteve */
    public String getLaneCode() {
        return laneCode;
    }

    /** @return a classe de permanência declarada à entrada */
    public StayClass getStayClass() {
        return stayClass;
    }

    /** @return a duração estimada em minutos, ou 0 se não foi indicada */
    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    /** @return a hora de entrada, nunca {@code null} */
    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    /** @return a hora de saída, ou {@code null} enquanto a sessão estiver activa */
    public LocalDateTime getExitTime() {
        return exitTime;
    }

    /** @return as manobras registadas ao fechar, ou 0 enquanto estiver activa */
    public int getManoeuvres() {
        return manoeuvres;
    }

    /** @return o estado, nunca {@code null} */
    public Status getStatus() {
        return status;
    }

    /** @return {@code true} se o carro ainda estiver no parque */
    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /**
     * @return o identificador na base de dados, ou 0 se a sessão ainda não
     *         foi gravada
     */
    public long getId() {
        return id;
    }

    /**
     * Regista o identificador atribuído pela base de dados. Só a camada
     * {@code persistence} chama este método, uma vez.
     *
     * @param id o identificador, maior que 0
     * @throws IllegalArgumentException se {@code id} não for positivo
     * @throws IllegalStateException se o identificador já tiver sido
     *         atribuído
     */
    public void setId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive: " + id);
        }
        if (this.id != 0) {
            throw new IllegalStateException("id already set: " + this.id);
        }
        this.id = id;
    }

     @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ParkingSession)) {
            return false;
        }
        return ticketNumber == ((ParkingSession) other).ticketNumber;
    }

    /** @return o talão e a matrícula, por exemplo {@code "#42 AAA123MC"} */
    @Override
    public String toString() {
        return "#" + ticketNumber + " " + vehicle.getPlate();
    }
}
