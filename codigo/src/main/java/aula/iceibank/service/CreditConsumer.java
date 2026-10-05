package aula.iceibank.service;

import aula.iceibank.dto.CreditEvent;
import aula.iceibank.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class CreditConsumer {
    private final TransferService transfers;
    private final ObjectMapper mapper;
    private final EventLogService logs;
    private final VectorClockService clock;
    public CreditConsumer(TransferService transfers, ObjectMapper mapper, EventLogService logs, VectorClockService clock) {
        this.transfers = transfers; this.mapper = mapper; this.logs = logs; this.clock = clock;
    }
    @RabbitListener(queues = "fila-agencia-${bank.agency-id}", autoStartup = "${bank.messaging-enabled:true}")
    public void receive(Message message) throws java.io.IOException {
        try {
            transfers.receiveCreditEvent(mapper.readValue(message.getBody(), CreditEvent.class));
        } catch (BusinessException | IllegalArgumentException | java.io.IOException exception) {
            logs.register("CREDIT_REJECTED", clock.localEvent(), exception.getMessage());
            throw new AmqpRejectAndDontRequeueException("Invalid credit sent to DLQ", exception);
        }
    }
}
