package aula.iceibank.config;

import aula.iceibank.entity.Agency;
import aula.iceibank.entity.Account;
import aula.iceibank.entity.AppUser;
import aula.iceibank.repository.AccountRepository;
import aula.iceibank.repository.AgencyRepository;
import aula.iceibank.repository.AppUserRepository;
import aula.iceibank.repository.EventLogRepository;
import aula.iceibank.service.VectorClockService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final AgencyRepository agencyRepository;
    private final AccountRepository accountRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BankProperties properties;
    private final EventLogRepository eventLogRepository;
    private final VectorClockService clock;

    public DataInitializer(AgencyRepository agencyRepository, AccountRepository accountRepository,
                           AppUserRepository userRepository,
                           PasswordEncoder passwordEncoder, BankProperties properties,
                           EventLogRepository eventLogRepository, VectorClockService clock) {
        this.agencyRepository = agencyRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.eventLogRepository = eventLogRepository;
        this.clock = clock;
    }

    @Override
    public void run(String... args) {
        for (int id = 0; id < 3; id++) {
            if (agencyRepository.findById(id).isEmpty()) {
                agencyRepository.save(new Agency(id, "Agency " + id, properties.agencyUrl(id)));
            }
        }
        if (userRepository.findByUsername("admin").isEmpty()) {
            userRepository.save(new AppUser("admin", passwordEncoder.encode("admin123"), null, "ADMIN"));
        }
        seedAccounts();
        seedAgencyUser();
        eventLogRepository.findAll().stream().filter(event -> event.getTimestampVetorial() != null).forEach(event -> clock.advanceTo(event.getTimestampVetorial()));
    }

    private void seedAccounts() {
        sampleAccounts().stream()
                .filter(account -> account.getAgencyId() == properties.getAgencyId())
                .filter(account -> accountRepository.findById(account.getAccountNumber()).isEmpty())
                .forEach(accountRepository::save);
    }

    private void seedAgencyUser() {
        String username = "cliente" + properties.getAgencyId();
        if (userRepository.findByUsername(username).isEmpty()) {
            long accountNumber = 3L + properties.getAgencyId();
            userRepository.save(new AppUser(username, passwordEncoder.encode("123456"), accountNumber, "USER"));
        }
    }

    private List<Account> sampleAccounts() {
        return List.of(
                new Account(3L, "Ana Souza", new BigDecimal("1500.00"), 0),
                new Account(6L, "Bruno Lima", new BigDecimal("800.00"), 0),
                new Account(4L, "Carla Mendes", new BigDecimal("1250.00"), 1),
                new Account(7L, "Daniel Rocha", new BigDecimal("600.00"), 1),
                new Account(5L, "Elisa Martins", new BigDecimal("2000.00"), 2),
                new Account(8L, "Felipe Costa", new BigDecimal("450.00"), 2)
        );
    }
}
