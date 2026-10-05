package aula.iceibank.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_logs")
public class EventLog {

    @Id
    private UUID id;
    private Integer agencyId;
    private String type;
    private long lamportTimestamp;
    @jakarta.persistence.Convert(converter = VectorConverter.class)
    private long[] timestampVetorial;

    public long[] getTimestampVetorial() { return timestampVetorial == null ? null : timestampVetorial.clone(); }
    private Instant wallClock;
    private String details;

    protected EventLog() {
    }

    public EventLog(Integer agencyId, String type, long lamportTimestamp, String details) {
        this.id = UUID.randomUUID();
        this.agencyId = agencyId;
        this.type = type;
        this.lamportTimestamp = lamportTimestamp;
        this.wallClock = Instant.now();
        this.details = details;
    }

    public EventLog(Integer agencyId, String type, long[] vector, String details) {
        this(agencyId, type, aula.iceibank.service.VectorClockService.rank(vector), details);
        this.timestampVetorial = vector.clone();
    }

    public UUID getId() {
        return id;
    }

    public Integer getAgencyId() {
        return agencyId;
    }

    public String getType() {
        return type;
    }

    public long getLamportTimestamp() {
        return lamportTimestamp;
    }

    public Instant getWallClock() {
        return wallClock;
    }

    public String getDetails() {
        return details;
    }
}
