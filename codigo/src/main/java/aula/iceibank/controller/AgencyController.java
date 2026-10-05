package aula.iceibank.controller;

import aula.iceibank.config.BankProperties;
import aula.iceibank.entity.Agency;
import aula.iceibank.repository.AgencyRepository;
import aula.iceibank.service.VectorClockService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agencies")
public class AgencyController {

    private final AgencyRepository repository;
    private final BankProperties properties;
    private final VectorClockService clock;

    public AgencyController(AgencyRepository repository, BankProperties properties, VectorClockService clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<List<Agency>> list() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> current() {
        return ResponseEntity.ok(Map.of("agencyId", properties.getAgencyId(), "timestampVetorial", clock.current()));
    }
}
