package aula.iceibank.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_transactions")
public class BankTransaction {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private TransactionType type;
    private Long sourceAccount;
    private Long destinationAccount;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    private Integer agencyId;
    private long lamportTimestamp;
    @jakarta.persistence.Convert(converter = VectorConverter.class)
    private long[] timestampVetorial;

    public long[] getTimestampVetorial() { return timestampVetorial == null ? null : timestampVetorial.clone(); }
    private Instant createdAt;
    private String message;

    protected BankTransaction() {
    }

    public BankTransaction(TransactionType type, Long sourceAccount, Long destinationAccount,
                           BigDecimal amount, TransactionStatus status, Integer agencyId,
                           long lamportTimestamp, String message) {
        this(UUID.randomUUID(), type, sourceAccount, destinationAccount, amount, status, agencyId,
                lamportTimestamp, message);
    }

    public BankTransaction(UUID id, TransactionType type, Long sourceAccount, Long destinationAccount,
                           BigDecimal amount, TransactionStatus status, Integer agencyId,
                           long lamportTimestamp, String message) {
        this.id = id;
        this.type = type;
        this.sourceAccount = sourceAccount;
        this.destinationAccount = destinationAccount;
        this.amount = amount;
        this.status = status;
        this.agencyId = agencyId;
        this.lamportTimestamp = lamportTimestamp;
        this.createdAt = Instant.now();
        this.message = message;
    }

    public BankTransaction(TransactionType type, Long source, Long destination, BigDecimal amount, TransactionStatus status, Integer agency, long[] vector, String message) {
        this(UUID.randomUUID(), type, source, destination, amount, status, agency, vector, message);
    }
    public BankTransaction(UUID id, TransactionType type, Long source, Long destination, BigDecimal amount, TransactionStatus status, Integer agency, long[] vector, String message) {
        this(id, type, source, destination, amount, status, agency, aula.iceibank.service.VectorClockService.rank(vector), message);
        this.timestampVetorial = vector.clone();
    }

    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public Long getSourceAccount() {
        return sourceAccount;
    }

    public Long getDestinationAccount() {
        return destinationAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Integer getAgencyId() {
        return agencyId;
    }

    public long getLamportTimestamp() {
        return lamportTimestamp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getMessage() {
        return message;
    }
}
