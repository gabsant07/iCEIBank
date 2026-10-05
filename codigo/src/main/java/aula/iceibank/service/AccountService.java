package aula.iceibank.service;

import aula.iceibank.config.BankProperties;
import aula.iceibank.dto.AccountResponse;
import aula.iceibank.dto.CreateAccountRequest;
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

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final AgencyRoutingService routingService;
    private final VectorClockService clock;
    private final EventLogService eventLogService;
    private final BankProperties properties;

    public AccountService(AccountRepository accountRepository, BankTransactionRepository transactionRepository,
                          AgencyRoutingService routingService, VectorClockService clock,
                          EventLogService eventLogService, BankProperties properties) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.routingService = routingService;
        this.clock = clock;
        this.eventLogService = eventLogService;
        this.properties = properties;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        routingService.requireLocal(request.accountNumber());
        if (accountRepository.existsById(request.accountNumber())) {
            throw new BusinessException(HttpStatus.CONFLICT, "Account already exists");
        }
        Account account = accountRepository.save(new Account(request.accountNumber(), request.holderName(),
                request.initialBalance(), properties.getAgencyId()));
        record(TransactionType.ACCOUNT_CREATED, account.getAccountNumber(), null, request.initialBalance(),
                "Account created");
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse find(long accountNumber) {
        routingService.requireLocal(accountNumber);
        Account account = getLocal(accountNumber);
        long[] timestamp = clock.localEvent();
        eventLogService.register(TransactionType.BALANCE_CHECKED.name(), timestamp,
                "Balance checked for account " + accountNumber);
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listLocal() {
        return accountRepository.findAllByAgencyId(properties.getAgencyId()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional
    public AccountResponse deposit(long accountNumber, BigDecimal amount) {
        routingService.requireLocal(accountNumber);
        Account account = getLocal(accountNumber);
        account.credit(amount);
        record(TransactionType.DEPOSIT, accountNumber, null, amount, "Deposit completed");
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse withdraw(long accountNumber, BigDecimal amount) {
        routingService.requireLocal(accountNumber);
        Account account = getLocal(accountNumber);
        ensureFunds(account, amount);
        account.debit(amount);
        record(TransactionType.WITHDRAWAL, accountNumber, null, amount, "Withdrawal completed");
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public List<BankTransaction> history(long accountNumber) {
        routingService.requireLocal(accountNumber);
        getLocal(accountNumber);
        return transactionRepository.findBySourceAccountOrDestinationAccountOrderByCreatedAtDesc(accountNumber, accountNumber);
    }

    Account getLocal(long accountNumber) {
        return accountRepository.findById(accountNumber)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    void ensureFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds");
        }
    }

    private void record(TransactionType type, Long source, Long destination, BigDecimal amount, String message) {
        long[] timestamp = clock.localEvent();
        transactionRepository.save(new BankTransaction(type, source, destination, amount, TransactionStatus.COMPLETED,
                properties.getAgencyId(), timestamp, message));
        eventLogService.register(type.name(), timestamp, message + ", account=" + source);
    }
}
