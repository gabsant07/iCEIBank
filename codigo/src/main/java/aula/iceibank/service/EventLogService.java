package aula.iceibank.service;

import aula.iceibank.config.BankProperties;
import aula.iceibank.entity.EventLog;
import aula.iceibank.repository.EventLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Service
public class EventLogService {

    private final EventLogRepository repository;
    private final BankProperties properties;
    private final ObjectMapper objectMapper;

    public EventLogService(EventLogRepository repository, BankProperties properties, ObjectMapper objectMapper) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public synchronized EventLog register(String type, long[] timestamp, String details) {
        EventLog event = repository.save(new EventLog(properties.getAgencyId(), type, timestamp, details));
        org.slf4j.LoggerFactory.getLogger(EventLogService.class).info("[Vetor {}] {} {}", timestamp, type, details);
        appendJsonLine(event);
        return event;
    }

    public List<EventLog> timeline() {
        return repository.findAllByOrderByLamportTimestampAscAgencyIdAscWallClockAsc();
    }

    private void appendJsonLine(EventLog event) {
        try {
            Path directory = Path.of(properties.getEventDirectory());
            Files.createDirectories(directory);
            Path file = directory.resolve("eventos-agencia-" + properties.getAgencyId() + ".jsonl");
            String line = objectMapper.writeValueAsString(event) + System.lineSeparator();
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize event", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write event log", exception);
        }
    }
}
