from dataclasses import dataclass


APP_TITLE = "主机信息探测台"

DEFAULT_PROBE_ITEMS = (
    "hostname",
    "ip_address",
    "mac_address",
    "os_info",
    "cpu_info",
    "memory_info",
)


@dataclass(frozen=True)
class RabbitMQConfig:
    host: str = "118.24.73.32"
    port: int = 15333
    virtual_host: str = "my_vhost"
    username: str = "admin"
    password: str = "123456"
    exchange: str = "sysinfo_exchange"
    routing_key: str = "sysinfo"
    heartbeat_routing_key: str = "status"
    heartbeat_interval_seconds: int = 3
    exchange_type: str = "direct"
    heartbeat: int = 60
    blocked_connection_timeout: int = 15
    socket_timeout: int = 5
    connection_attempts: int = 1
    retry_delay: int = 0
