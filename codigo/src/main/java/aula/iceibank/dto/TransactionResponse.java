package aula.iceibank.dto;

import aula.iceibank.entity.BankTransaction;
import aula.iceibank.entity.TransactionStatus;
import aula.iceibank.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        TransactionType type,
        Long sourceAccount,
        Long destinationAccount,
        BigDecimal amount,
        TransactionStatus status,
        Integer agencyId,
        long lamportTimestamp,
        long[] timestampVetorial,
        Instant createdAt,
        String message
) {
    public static TransactionResponse from(BankTransaction transaction) {
        return new TransactionResponse(transaction.getId(), transaction.getType(), transaction.getSourceAccount(),
                transaction.getDestinationAccount(), transaction.getAmount(), transaction.getStatus(),
                transaction.getAgencyId(), transaction.getLamportTimestamp(), transaction.getTimestampVetorial(), transaction.getCreatedAt(),
                transaction.getMessage());
    }
}
