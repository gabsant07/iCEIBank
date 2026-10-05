package aula.iceibank.service;

import aula.iceibank.config.MessagingConfig;
import aula.iceibank.dto.CreditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
public class MessagingService {
    private final RabbitTemplate template;
    private final ObjectMapper mapper;
    public MessagingService(RabbitTemplate template, ObjectMapper mapper) {
        this.template = template; this.mapper = mapper;
        template.setMandatory(true);
    }
    public void publish(int destination, CreditEvent event) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(event.transactionId().toString());
            CorrelationData correlation = new CorrelationData(event.transactionId().toString());
            template.send(MessagingConfig.EXCHANGE, "agencia." + destination + ".creditar",
                    new Message(mapper.writeValueAsBytes(event), properties), correlation);
            var confirmation = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirmation.isAck() || correlation.getReturned() != null)
                throw new IllegalStateException("Broker rejected or could not route the credit");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publication interrupted; delivery uncertain", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Credit publication not confirmed; delivery uncertain", exception);
        }
    }
}
