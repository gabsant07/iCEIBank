package aula.iceibank.service;

import aula.iceibank.dto.CreditEvent;
import aula.iceibank.dto.TransferRequest;
import aula.iceibank.entity.Account;
import aula.iceibank.entity.TransactionStatus;
import aula.iceibank.repository.AccountRepository;
import aula.iceibank.repository.BankTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:messaging-test;DB_CLOSE_DELAY=-1")
class MessagingTransferTest {
    @Autowired TransferService transfers;
    @Autowired AccountRepository accounts;
    @Autowired BankTransactionRepository transactions;
    @MockitoBean MessagingService messaging;

    @Test void publishesWithoutCallingDestinationAndKeepsDebit() {
        accounts.save(new Account(300L,"Sender",new BigDecimal("100.00"),0));
        var result = transfers.transfer(new TransferRequest(300L,301L,new BigDecimal("25.00")));
        assertEquals(TransactionStatus.PUBLISHED,result.status());
        assertEquals(0,accounts.findById(300L).orElseThrow().getBalance().compareTo(new BigDecimal("75.00")));
        verify(messaging).publish(eq(1),argThat(event -> event.transactionId().equals(result.id())
                && event.timestampVetorial()[0] > 0));
    }
    @Test void receivesVectorAndDoesNotCreditTwice() {
        accounts.save(new Account(303L,"Receiver",new BigDecimal("10.00"),0));
        CreditEvent credit = new CreditEvent(UUID.randomUUID(),304L,303L,new BigDecimal("20.00"),1,new long[]{0,5,0});
        transfers.receiveCreditEvent(credit);
        transfers.receiveCreditEvent(credit);
        assertEquals(0,accounts.findById(303L).orElseThrow().getBalance().compareTo(new BigDecimal("30.00")));
        long[] vector = transactions.findById(credit.transactionId()).orElseThrow().getTimestampVetorial();
        assertTrue(vector[0]>0);
        assertTrue(vector[1]>=5);
    }
    @Test void missingAccountDoesNotCreateFalseCompletedTransaction() {
        CreditEvent credit = new CreditEvent(UUID.randomUUID(),310L,309L,new BigDecimal("20.00"),1,new long[]{0,6,0});
        assertThrows(aula.iceibank.exception.BusinessException.class,()->transfers.receiveCreditEvent(credit));
        assertFalse(transactions.existsById(credit.transactionId()));
    }
    @Test void brokerFailureIsRecordedAsUncertainWithoutClaimingCredit() {
        accounts.save(new Account(312L,"Sender",new BigDecimal("100.00"),0));
        doThrow(new IllegalStateException("Broker down")).when(messaging).publish(eq(1),any());
        assertThrows(aula.iceibank.exception.BusinessException.class,
                ()->transfers.transfer(new TransferRequest(312L,313L,new BigDecimal("25.00"))));
        assertEquals(0,accounts.findById(312L).orElseThrow().getBalance().compareTo(new BigDecimal("75.00")));
        assertEquals(TransactionStatus.INCONSISTENT,transactions
                .findBySourceAccountOrDestinationAccountOrderByCreatedAtDesc(312L,312L).getFirst().getStatus());
    }
}
