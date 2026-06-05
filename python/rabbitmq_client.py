import json

import pika


def build_connection_parameters(config):
    credentials = pika.PlainCredentials(
        username=config.username,
        password=config.password,
    )
    return pika.ConnectionParameters(
        host=config.host,
        port=config.port,
        virtual_host=config.virtual_host,
        credentials=credentials,
        heartbeat=config.heartbeat,
        blocked_connection_timeout=config.blocked_connection_timeout,
        socket_timeout=config.socket_timeout,
        connection_attempts=config.connection_attempts,
        retry_delay=config.retry_delay,
    )


class RabbitMQPublisher:
    def __init__(self, config, logger=None):
        self.config = config
        self.logger = logger
        self.connection = None
        self.channel = None

    def connect(self, exchange=None):
        if self.connection and self.connection.is_open and self.channel and self.channel.is_open:
            self._declare_exchange(exchange or self.config.exchange)
            return

        parameters = build_connection_parameters(self.config)
        target_exchange = exchange or self.config.exchange

        if self.logger:
            self.logger.info(
                "Connecting to RabbitMQ: %s:%s vhost=%s",
                self.config.host,
                self.config.port,
                self.config.virtual_host,
            )

        self.connection = pika.BlockingConnection(parameters)
        self.channel = self.connection.channel()
        self._declare_exchange(target_exchange)

        if self.logger:
            self.logger.info(
                "RabbitMQ connected, exchange ready: %s",
                target_exchange,
            )

    def publish_dict(self, payload, routing_key=None, exchange=None):
        target_exchange = exchange or self.config.exchange
        self.connect(exchange=target_exchange)
        body = json.dumps(payload, ensure_ascii=False)
        target_routing_key = routing_key or self.config.routing_key
        self.channel.basic_publish(
            exchange=target_exchange,
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
                "Message published successfully, exchange=%s, routing_key=%s, payload=%s",
                target_exchange,
                target_routing_key,
                body,
            )

    def _declare_exchange(self, exchange):
        self.channel.exchange_declare(
            exchange=exchange,
            exchange_type=self.config.exchange_type,
            durable=True,
        )

    def close(self):
        if self.channel and self.channel.is_open:
            self.channel.close()
        if self.connection and self.connection.is_open:
            self.connection.close()
        if self.logger:
            self.logger.info("RabbitMQ connection closed.")
