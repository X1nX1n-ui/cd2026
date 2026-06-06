package com.cd.server;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final String EMAIL_EXCHANGE = "email_exchange";
    private static final String EMAIL_QUEUE = "email_verification_queue";
    private static final String EMAIL_ROUTING_KEY = "email.verification";

    private final RabbitTemplate rabbitTemplate;
    private final AmqpAdmin amqpAdmin;

    public EmailVerificationService(RabbitTemplate rabbitTemplate, AmqpAdmin amqpAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.amqpAdmin = amqpAdmin;
        ensureResources();
    }

    private void ensureResources() {
        DirectExchange exchange = new DirectExchange(EMAIL_EXCHANGE, true, false);
        amqpAdmin.declareExchange(exchange);

        Queue queue = new Queue(EMAIL_QUEUE, true);
        amqpAdmin.declareQueue(queue);

        Binding binding = BindingBuilder.bind(queue).to(exchange).with(EMAIL_ROUTING_KEY);
        amqpAdmin.declareBinding(binding);

        log.info("Email verification RabbitMQ resources ensured. exchange={}, queue={}", EMAIL_EXCHANGE, EMAIL_QUEUE);
    }

    /**
     * ??????????RabbitMQ??
     * @param email ??????
     * @param code 6????
     * @param expireMinutes ??????????
     */
    public void sendVerificationCode(String email, String code, int expireMinutes) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("email", email);
        message.put("code", code);
        message.put("expireMinutes", expireMinutes);
        message.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        message.put("type", "email_verification");

        String jsonPayload = JSON.toJSONString(message);

        rabbitTemplate.convertAndSend(EMAIL_EXCHANGE, EMAIL_ROUTING_KEY, jsonPayload);
        log.info("Email verification code sent to queue. email={}, code={}", email, code);
    }
}
