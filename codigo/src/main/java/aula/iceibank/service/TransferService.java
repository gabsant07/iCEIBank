package aula.iceibank.service;

import aula.iceibank.config.BankProperties;
import aula.iceibank.dto.MessageResponse;
import aula.iceibank.dto.RemoteCreditRequest;
import aula.iceibank.dto.TransactionResponse;
import aula.iceibank.dto.TransferRequest;
import aula.iceibank.entity.Account;
import aula.iceibank.entity.BankTransaction;
import aula.iceibank.entity.TransactionStatus;
import aula.iceibank.entity.TransactionType;
import aula.iceibank.exception.BusinessException;
import aula.iceibank.repository.AccountRepository;
import aula.iceibank.repository.BankTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final AccountService accountService;
    private final RemoteDebitService remoteDebitService;
    private final AgencyRoutingService routingService;
    private final VectorClockService clock;
    private final EventLogService eventLogService;
    private final BankProperties properties;
    private final MessagingService messaging;
    private final TransactionTemplate transactionTemplate;

    public TransferService(AccountRepository accountRepository, BankTransactionRepository transactionRepository,
                           AccountService accountService, RemoteDebitService remoteDebitService,
                           AgencyRoutingService routingService, VectorClockService clock,
                           EventLogService eventLogService, BankProperties properties, MessagingService messaging,
                           TransactionTemplate transactionTemplate) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accountService = accountService;
        this.remoteDebitService = remoteDebitService;
        this.routingService = routingService;
        this.clock = clock;
        this.eventLogService = eventLogService;
        this.properties = properties;
        this.messaging = messaging;
        this.transactionTemplate = transactionTemplate;
    }

    public TransactionResponse transfer(TransferRequest request) {
        routingService.requireLocal(request.sourceAccount());
        if (request.sourceAccount().equals(request.destinationAccount())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Source and destination must be different");
        }
        int destinationAgency = routingService.ownerOf(request.destinationAccount());
        return destinationAgency == properties.getAgencyId()
                ? localTransfer(request)
                : remoteTransfer(request, destinationAgency);
    }

    public TransactionResponse localTransfer(TransferRequest request) {
        return transactionTemplate.execute(status -> {
            Account source = accountService.getLocal(request.sourceAccount());
            Account destination = accountService.getLocal(request.destinationAccount());
            accountService.ensureFunds(source, request.amount());
            source.debit(request.amount());
            destination.credit(request.amount());
            accountRepository.save(source);
            accountRepository.save(destination);
            long[] timestamp = clock.localEvent();
            BankTransaction transaction = transactionRepository.save(new BankTransaction(TransactionType.LOCAL_TRANSFER,
                    source.getAccountNumber(), destination.getAccountNumber(), request.amount(), TransactionStatus.COMPLETED,
                    properties.getAgencyId(), timestamp, "Local transfer completed"));
            eventLogService.register(TransactionType.LOCAL_TRANSFER.name(), timestamp,
                    "Local transfer from " + source.getAccountNumber() + " to " + destination.getAccountNumber());
            return TransactionResponse.from(transaction);
        });
    }

    public TransactionResponse remoteTransfer(TransferRequest request, int destinationAgency) {
        remoteDebitService.debitCommitted(request.sourceAccount(), request.amount());
        UUID transactionId = UUID.randomUUID();
        long[] sendTimestamp = clock.sendEvent();
        aula.iceibank.dto.CreditEvent credit = new aula.iceibank.dto.CreditEvent(transactionId, request.sourceAccount(),
                request.destinationAccount(), request.amount(), properties.getAgencyId(), sendTimestamp);
        try {
            messaging.publish(destinationAgency, credit);
            BankTransaction transaction = transactionRepository.save(new BankTransaction(transactionId,
                    TransactionType.REMOTE_TRANSFER_SENT, request.sourceAccount(), request.destinationAccount(),
                    request.amount(), TransactionStatus.PUBLISHED, properties.getAgencyId(), sendTimestamp,
                    "Published to RabbitMQ; destination credit is asynchronous"));
            eventLogService.register(TransactionType.REMOTE_TRANSFER_SENT.name(), sendTimestamp,
                    "Published transfer " + transactionId + " to agency " + destinationAgency);
            return TransactionResponse.from(transaction);
        } catch (RuntimeException exception) {
            BankTransaction transaction = transactionRepository.save(new BankTransaction(transactionId,
                    TransactionType.REMOTE_TRANSFER_FAILED, request.sourceAccount(), request.destinationAccount(),
                    request.amount(), TransactionStatus.INCONSISTENT, properties.getAgencyId(), sendTimestamp,
                    "Debit committed; broker publication unconfirmed; delivery uncertain"));
            eventLogService.register(TransactionType.REMOTE_TRANSFER_FAILED.name(), sendTimestamp,
                    "Transfer " + transactionId + ": debit committed; broker confirmation missing");
            throw new BusinessException(HttpStatus.BAD_GATEWAY,
                    "Broker publication unconfirmed. Debit was not automatically reverted; delivery may have occurred. Transaction: "
                            + transaction.getId());
        }
    }

    @Transactional
    public MessageResponse receiveRemoteCredit(RemoteCreditRequest request) {
        throw new BusinessException(HttpStatus.GONE, "Use RabbitMQ for inter-agency credits");
    }
    @Transactional
    public MessageResponse receiveCreditEvent(aula.iceibank.dto.CreditEvent request) {
        VectorClockService.validate(request.timestampVetorial());
        if (request.transactionId() == null || request.sourceAccount() == null || request.destinationAccount() == null
                || request.amount() == null || request.amount().compareTo(new java.math.BigDecimal("0.01")) < 0)
            throw new IllegalArgumentException("Invalid credit payload");
        routingService.requireLocal(request.destinationAccount());
        if (request.sourceAgency() < 0 || request.sourceAgency() > 2
                || routingService.ownerOf(request.sourceAccount()) != request.sourceAgency()
                || request.timestampVetorial()[request.sourceAgency()] == 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Invalid inter-agency transfer data");
        }
        if (transactionRepository.existsById(request.transactionId())) {
            return new MessageResponse("Credit already processed");
        }
        long[] timestamp = clock.receiveEvent(request.timestampVetorial());
        Account destination = accountService.getLocal(request.destinationAccount());
        destination.credit(request.amount());
        accountRepository.save(destination);
        transactionRepository.save(new BankTransaction(request.transactionId(),
                TransactionType.REMOTE_TRANSFER_RECEIVED, request.sourceAccount(), request.destinationAccount(),
                request.amount(), TransactionStatus.COMPLETED, properties.getAgencyId(), timestamp,
                "Remote credit received from agency " + request.sourceAgency()));
        eventLogService.register(TransactionType.REMOTE_TRANSFER_RECEIVED.name(), timestamp,
                "Remote credit from agency " + request.sourceAgency() + " for account " + request.destinationAccount());
        return new MessageResponse("Remote credit completed");
    }
}
