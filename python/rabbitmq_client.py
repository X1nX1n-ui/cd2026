import json

import pika


class RabbitMQPublisher:
    def __init__(self, config, logger=None):
        self.config = config
        self.logger = logger
        self.connection = None
        self.channel = None

    def connect(self):
        if self.connection and self.connection.is_open and self.channel and self.channel.is_open:
            return

        credentials = pika.PlainCredentials(
            username=self.config.username,
            password=self.config.password,
        )
        parameters = pika.ConnectionParameters(
            host=self.config.host,
            port=self.config.port,
            virtual_host=self.config.virtual_host,
            credentials=credentials,
            heartbeat=self.config.heartbeat,
            blocked_connection_timeout=self.config.blocked_connection_timeout,
            socket_timeout=self.config.socket_timeout,
            connection_attempts=self.config.connection_attempts,
            retry_delay=self.config.retry_delay,
        )

        if self.logger:
            self.logger.info(
                "Connecting to RabbitMQ: %s:%s vhost=%s",
                self.config.host,
                self.config.port,
                self.config.virtual_host,
            )

        self.connection = pika.BlockingConnection(parameters)
        self.channel = self.connection.channel()
        self.channel.exchange_declare(
            exchange=self.config.exchange,
            exchange_type=self.config.exchange_type,
            durable=True,
        )

        if self.logger:
            self.logger.info(
                "RabbitMQ connected, exchange ready: %s",
                self.config.exchange,
            )

    def publish_dict(self, payload, routing_key=None):
        self.connect()
        body = json.dumps(payload, ensure_ascii=False)
        target_routing_key = routing_key or self.config.routing_key
        self.channel.basic_publish(
            exchange=self.config.exchange,
            routing_key=target_routing_key,
            body=body.encode("utf-8"),
            properties=pika.BasicProperties(
                content_type="application/json",
                content_encoding="utf-8",
                delivery_mode=pika.DeliveryMode.Persistent,
            ),
        )

        if self.logger:
            self.logger.info(
                "Message published successfully, routing_key=%s, payload=%s",
                target_routing_key,
                body,
            )

    def close(self):
        if self.channel and self.channel.is_open:
            self.channel.close()
        if self.connection and self.connection.is_open:
            self.connection.close()
        if self.logger:
            self.logger.info("RabbitMQ connection closed.")
