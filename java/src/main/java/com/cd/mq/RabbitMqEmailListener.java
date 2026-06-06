package com.cd.mq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cd.server.EmailSenderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class RabbitMqEmailListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqEmailListener.class);

    private static final String EMAIL_QUEUE = "email_verification_queue";
    private static final String EMAIL_EXCHANGE = "email_exchange";
    private static final String EMAIL_ROUTING_KEY = "email.verification";

    private final EmailSenderService emailSenderService;

    public RabbitMqEmailListener(EmailSenderService emailSenderService) {
        this.emailSenderService = emailSenderService;
    }

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue(name = EMAIL_QUEUE, durable = "true"),
            exchange = @Exchange(name = EMAIL_EXCHANGE, type = "direct", durable = "true"),
            key = EMAIL_ROUTING_KEY
        )
    )
    public void onEmailVerificationMessage(String payload) {
        JSONObject jsonObject = JSON.parseObject(payload);
        String email = jsonObject.getString("email");
        String code = jsonObject.getString("code");
        Integer expireMinutes = jsonObject.getInteger("expireMinutes");
        String type = jsonObject.getString("type");

        if (!"email_verification".equals(type)) {
            log.warn("?????????: {}", type);
            return;
        }

        try {
            emailSenderService.sendVerificationCode(email, code, expireMinutes);
            log.info("?????????, email={}", email);
        } catch (Exception e) {
            log.error("?????????, email={}, error={}", email, e.getMessage(), e);
            throw new RuntimeException("??????: " + e.getMessage(), e);
        }
    }
}
