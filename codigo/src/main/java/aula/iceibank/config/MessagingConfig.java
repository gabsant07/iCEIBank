package aula.iceibank.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class MessagingConfig {
    public static final String EXCHANGE = "iceibank.eventos";
    @Bean
    ConnectionFactory rabbitConnectionFactory(Environment environment) {
        String url = environment.getProperty("RABBITMQ_URL");
        if (url == null || url.isBlank()) {
            if (!environment.getProperty("bank.messaging-enabled", Boolean.class, true)) url = "amqp://localhost";
            else throw new IllegalStateException("Defina RABBITMQ_URL antes de iniciar a agência");
        }
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setUri(url);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }
    @Bean
    Declarables topology() {
        TopicExchange events = new TopicExchange(EXCHANGE, true, false);
        DirectExchange dead = new DirectExchange("iceibank.rejeitados", true, false);
        List<Declarable> declarations = new ArrayList<>(List.of(events, dead));
        for (int id = 0; id < 3; id++) {
            Queue queue = QueueBuilder.durable("fila-agencia-" + id)
                    .deadLetterExchange(dead.getName()).deadLetterRoutingKey("agencia." + id).build();
            Queue dlq = QueueBuilder.durable("fila-agencia-" + id + ".dlq").build();
            declarations.add(queue);
            declarations.add(dlq);
            declarations.add(BindingBuilder.bind(queue).to(events).with("agencia." + id + ".creditar"));
            declarations.add(BindingBuilder.bind(dlq).to(dead).with("agencia." + id));
        }
        return new Declarables(declarations);
    }
}
