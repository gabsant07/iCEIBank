package aula.iceibank.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreditEvent(UUID transactionId, Long sourceAccount, Long destinationAccount,
                          BigDecimal amount, int sourceAgency, long[] timestampVetorial) {}
