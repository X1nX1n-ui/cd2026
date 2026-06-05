import ctypes
import json
import logging
import pprint
import sys
import threading
import tkinter as tk
from datetime import datetime
from tkinter import messagebox
from tkinter import scrolledtext
from tkinter import ttk

import pika

from config import APP_TITLE
from config import DEFAULT_PROBE_ITEMS
from config import RabbitMQConfig
from rabbitmq_client import build_connection_parameters
from rabbitmq_client import RabbitMQPublisher
from system_probe import HostInfoCollector


class ToggleableConsoleHandler(logging.StreamHandler):
    def __init__(self, stream=None, enabled=False):
        super().__init__(stream=stream)
        self._enabled = enabled

    def set_enabled(self, enabled):
        self._enabled = enabled

    def emit(self, record):
        if not self._enabled:
            return
        super().emit(record)


class HeartbeatService:
    def __init__(self, collector, config, logger):
        self.collector = collector
        self.config = config
        self.logger = logger
        self.stop_event = threading.Event()
        self.thread = None
        self.publisher = None

    def start(self):
        if self.thread and self.thread.is_alive():
            return

        self.stop_event.clear()
        self.thread = threading.Thread(
            target=self._run,
            name="heartbeat-worker",
            daemon=True,
        )
        self.thread.start()

    def stop(self):
        self.stop_event.set()
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=2)
        if self.publisher:
            self.publisher.close()

    def _run(self):
        self.publisher = RabbitMQPublisher(self.config, self.logger)
        self.logger.info(
            "Heartbeat thread started. interval=%s seconds, routing_key=%s",
            self.config.heartbeat_interval_seconds,
            self.config.heartbeat_routing_key,
        )

        while not self.stop_event.is_set():
            payload = self.collector.get_mac_address()
            self.logger.info("Heartbeat: %s", payload)

            try:
                self.publisher.publish_dict(
                    payload,
                    routing_key=self.config.heartbeat_routing_key,
                )
            except Exception:
                self.logger.exception("Failed to publish heartbeat message.")
                if self.publisher:
                    self.publisher.close()
                    self.publisher = RabbitMQPublisher(self.config, self.logger)

            if self.stop_event.wait(self.config.heartbeat_interval_seconds):
                break

        if self.publisher:
            self.publisher.close()
        self.logger.info("Heartbeat thread stopped.")


class AssetProbeListenerService:
    def __init__(self, collector, config, logger):
        self.collector = collector
        self.config = config
        self.logger = logger
        self.stop_event = threading.Event()
        self.thread = None
        self.connection = None
        self.channel = None
        self.publisher = None
        self.queue_name = None
        self.local_mac_address = self._normalize_mac_address(
            self.collector.get_mac_address().get("mac_address", "")
        )

    def start(self):
        if self.thread and self.thread.is_alive():
            return

        self.queue_name = "agent_{0}_queue".format(self.local_mac_address)
        self.stop_event.clear()
        self.thread = threading.Thread(
            target=self._run,
            name="asset-probe-listener",
            daemon=True,
        )
        self.thread.start()

    def stop(self):
        self.stop_event.set()
        self._close_connection()
        if self.publisher:
            self.publisher.close()
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=3)

    def _run(self):
        self.logger.info(
            "Asset probe listener started. queue=%s, mac=%s",
            self.queue_name,
            self.local_mac_address,
        )

        while not self.stop_event.is_set():
            try:
                self._consume_messages()
            except Exception:
                if self.stop_event.is_set():
                    break
                self.logger.exception("Asset probe listener failed, retrying.")
                self._close_connection()
                if self.stop_event.wait(3):
                    break

        self._close_connection()
        self.logger.info("Asset probe listener stopped.")

    def _consume_messages(self):
        self._connect()
        for method_frame, _, body in self.channel.consume(
            queue=self.queue_name,
            inactivity_timeout=1,
            auto_ack=False,
        ):
            if self.stop_event.is_set():
                self.channel.cancel()
                break

            if method_frame is None:
                continue

            try:
                payload = json.loads(body.decode("utf-8"))
            except Exception:
                self.logger.exception("Failed to decode asset probe message: %s", body)
                self.channel.basic_ack(method_frame.delivery_tag)
                continue

            try:
                self._handle_message(payload)
            except Exception:
                self.logger.exception("Failed to handle asset probe message: %s", payload)
            finally:
                self.channel.basic_ack(method_frame.delivery_tag)

    def _handle_message(self, payload):
        if payload.get("type") != "assets":
            self.logger.info("Ignored non-assets message: %s", payload)
            return

        if not self._is_probe_command(payload):
            self.logger.info("Ignored assets message without probe flags: %s", payload)
            return

        message_mac = self._normalize_mac_address(payload.get("macAddress"))
        if message_mac and message_mac != self.local_mac_address:
            self.logger.info(
                "Ignored assets message for another host. local=%s, message=%s",
                self.local_mac_address,
                message_mac,
            )
            return

        result = self.collector.collect_asset_payload(payload)
        self._publish_asset_results(result)
        self.logger.info("Asset probe completed for message: %s", payload)

    def _connect(self):
        if self.connection and self.connection.is_open and self.channel and self.channel.is_open:
            return

        parameters = build_connection_parameters(self.config)
        self.connection = pika.BlockingConnection(parameters)
        self.channel = self.connection.channel()
        self.channel.exchange_declare(
            exchange=self.config.exchange,
            exchange_type=self.config.exchange_type,
            durable=True,
        )
        self.channel.queue_declare(queue=self.queue_name, durable=True)
        self.channel.queue_bind(
            exchange=self.config.exchange,
            queue=self.queue_name,
            routing_key=self.queue_name,
        )

    def _close_connection(self):
        try:
            if self.channel and self.channel.is_open:
                self.channel.close()
        except Exception:
            pass
        try:
            if self.connection and self.connection.is_open:
                self.connection.close()
        except Exception:
            pass
        self.channel = None
        self.connection = None

    def _publish_asset_results(self, result):
        if not self.publisher:
            self.publisher = RabbitMQPublisher(self.config, self.logger)

        for probe_type, payload in self._build_publish_payloads(result):
            self.publisher.publish_dict(
                payload,
                routing_key=probe_type,
                exchange=self.config.exchange,
            )

    @staticmethod
    def _normalize_mac_address(mac_address):
        if not mac_address:
            return ""
        normalized = str(mac_address).strip().replace(":", "-").upper()
        return normalized

    @staticmethod
    def _is_probe_command(payload):
        probe_fields = ("account", "service", "process", "app")
        for field in probe_fields:
            try:
                if int(payload.get(field, 0) or 0) == 1:
                    return True
            except (TypeError, ValueError):
                continue
        return False

    @staticmethod
    def _build_publish_payloads(result):
        mac_address = result.get("mac_address")
        payloads = []
        publish_specs = (
            ("account", ("accounts", "shadow_accounts", "account_count", "shadow_account_count")),
            ("service", ("services", "service_count")),
            ("process", ("processes", "process_count")),
            ("app", ("apps", "app_count")),
        )

        for probe_type, fields in publish_specs:
            payload = {"mac_address": mac_address}
            has_data = False

            for field in fields:
                value = result.get(field)
                if value is None:
                    continue
                payload[field] = value
                if isinstance(value, list) and value:
                    has_data = True
                elif isinstance(value, int) and value > 0:
                    has_data = True

            if has_data:
                payloads.append((probe_type, payload))

        return payloads


class HostInspectorService:
    def __init__(self, collector, publisher, heartbeat_service, asset_listener_service, logger):
        self.collector = collector
        self.publisher = publisher
        self.heartbeat_service = heartbeat_service
        self.asset_listener_service = asset_listener_service
        self.logger = logger

    def start(self):
        self.heartbeat_service.start()
        self.asset_listener_service.start()

    def collect_selected_info(self, selected_items):
        self.logger.info("Starting manual probe: %s", ", ".join(selected_items))
        result = self.collector.collect_selected(selected_items)
        self.logger.info("Probe finished, fields collected: %s", len(result))
        return result

    def collect_and_publish_startup_info(self):
        self.logger.info("Collecting startup host info and sending to RabbitMQ.")
        payload = self.collector.collect_startup_payload()
        self.publisher.publish_dict(payload)
        self.logger.info("Startup host info sent to RabbitMQ.")
        return payload

    def close(self):
        self.asset_listener_service.stop()
        self.heartbeat_service.stop()
        self.publisher.close()


class HostInspectorApp:
    def __init__(self, root, service, logger, log_handler):
        self.root = root
        self.service = service
        self.logger = logger
        self.log_handler = log_handler
        self.palette = {
            "bg": "#0b0e14",
            "surface": "#151921",
            "surface_alt": "#10131a",
            "surface_panel": "#1a202b",
            "border": "#2c3442",
            "border_soft": "#364050",
            "text": "#e1e2eb",
            "text_muted": "#c2c6d6",
            "text_soft": "#8c909f",
            "primary": "#4f86f7",
            "primary_alt": "#78a4ff",
            "primary_soft": "#142747",
            "success": "#c8dbff",
            "warning": "#f7c66c",
            "danger": "#f08b9f",
            "track": "#22324a",
            "result_bg": "#0f141d",
        }

        self.root.title(APP_TITLE)
        self.root.geometry("1280x820")
        self.root.minsize(1100, 720)
        self.root.configure(bg=self.palette["bg"])
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

        self.option_vars = {
            "hostname": tk.BooleanVar(value=True),
            "ip_address": tk.BooleanVar(value=True),
            "mac_address": tk.BooleanVar(value=True),
            "os_info": tk.BooleanVar(value=True),
            "cpu_info": tk.BooleanVar(value=True),
            "memory_info": tk.BooleanVar(value=True),
        }
        self.log_enabled_var = tk.BooleanVar(value=False)

        self.option_labels = {
            "hostname": "获取主机名",
            "ip_address": "获取 IP 地址",
            "mac_address": "获取 MAC 地址",
            "os_info": "获取操作系统信息",
            "cpu_info": "获取 CPU 信息",
            "memory_info": "获取内存信息",
        }

        self.status_var = tk.StringVar(value="等待启动任务...")
        self.permission_var = tk.StringVar()
        self.mq_var = tk.StringVar(value="RabbitMQ 状态: 尚未发送")
        self.result_summary_var = tk.StringVar(value="结果面板已就绪，等待最新探测数据。")
        self.runtime_var = tk.StringVar(
            value="运行环境  Python {0}.{1}".format(sys.version_info.major, sys.version_info.minor)
        )
        self.listener_var = tk.StringVar(
            value="监听队列  agent_{0}_queue".format(
                self.service.asset_listener_service.local_mac_address or "UNKNOWN"
            )
        )
        self.permission_badge = None

        self._configure_style()
        self._build_ui()
        self._set_permission_status()
        self.root.after(100, self.service.start)
        self.root.after(200, self.startup_collect_and_publish)

    def _configure_style(self):
        style = ttk.Style()
        style.theme_use("clam")
        style.configure(
            "Primary.TButton",
            background=self.palette["primary"],
            foreground="#eef2f7",
            font=("Microsoft YaHei UI", 11, "bold"),
            padding=(18, 12),
            borderwidth=1,
            relief="flat",
            focusthickness=0,
        )
        style.map(
            "Primary.TButton",
            background=[
                ("active", self.palette["primary_alt"]),
                ("pressed", self.palette["primary_alt"]),
            ],
            foreground=[("disabled", self.palette["text_soft"])],
        )
        style.configure(
            "Secondary.TButton",
            background=self.palette["surface_panel"],
            foreground=self.palette["text"],
            font=("Microsoft YaHei UI", 11),
            padding=(18, 12),
            borderwidth=1,
            relief="flat",
            focusthickness=0,
        )
        style.map(
            "Secondary.TButton",
            background=[
                ("active", self.palette["track"]),
                ("pressed", self.palette["surface_panel"]),
            ],
            foreground=[("disabled", self.palette["text_soft"])],
        )
        style.configure(
            "Tech.Vertical.TScrollbar",
            background=self.palette["surface_panel"],
            darkcolor=self.palette["surface_panel"],
            lightcolor=self.palette["surface_panel"],
            troughcolor=self.palette["surface_alt"],
            bordercolor=self.palette["surface_alt"],
            arrowcolor=self.palette["primary_alt"],
            gripcount=0,
        )

    def _build_ui(self):
        outer = tk.Frame(self.root, bg=self.palette["bg"])
        outer.pack(fill="both", expand=True, padx=24, pady=22)

        header_card = self._create_card(outer, self.palette["surface"], pady=0)
        header_card.pack(fill="x")

        header_glow = tk.Frame(header_card, bg=self.palette["primary"], height=3)
        header_glow.pack(fill="x")

        header_body = tk.Frame(header_card, bg=self.palette["surface"])
        header_body.pack(fill="x", padx=24, pady=22)
        header_body.grid_columnconfigure(0, weight=3)
        header_body.grid_columnconfigure(1, weight=2)

        hero = tk.Frame(header_body, bg=self.palette["surface"])
        hero.grid(row=0, column=0, sticky="nsew", padx=(0, 18))

        tk.Label(
            hero,
            text="HOST SENTINEL",
            bg=self.palette["surface"],
            fg=self.palette["text_soft"],
            font=("Consolas", 11, "bold"),
            anchor="w",
        ).pack(anchor="w")
        tk.Label(
            hero,
            text=APP_TITLE,
            bg=self.palette["surface"],
            fg=self.palette["text"],
            font=("Microsoft YaHei UI", 28, "bold"),
            anchor="w",
        ).pack(anchor="w", pady=(8, 4))
        tk.Label(
            hero,
            text="深灰蓝科技风主机探测控制台，启动自动上报、心跳维持在线、支持手动探测与资产监听。",
            bg=self.palette["surface"],
            fg=self.palette["text_muted"],
            font=("Microsoft YaHei UI", 11),
            anchor="w",
            justify="left",
        ).pack(anchor="w", pady=(0, 14))

        tag_row = tk.Frame(hero, bg=self.palette["surface"])
        tag_row.pack(anchor="w")
        for text in ("启动即上报", "心跳在线", "资产监听", "RabbitMQ 同步"):
            self._create_tag(tag_row, text).pack(side="left", padx=(0, 10))

        metrics = tk.Frame(header_body, bg=self.palette["surface"])
        metrics.grid(row=0, column=1, sticky="nsew")
        self._create_metric_tile(metrics, "权限级别", self.permission_var).pack(fill="x")
        self._create_metric_tile(metrics, "运行环境", self.runtime_var).pack(fill="x", pady=10)
        self._create_metric_tile(metrics, "监听队列", self.listener_var).pack(fill="x")

        content = tk.Frame(outer, bg=self.palette["bg"])
        content.pack(fill="both", expand=True, pady=(18, 0))
        content.grid_columnconfigure(0, weight=4)
        content.grid_columnconfigure(1, weight=7)
        content.grid_rowconfigure(0, weight=1)

        left_column = tk.Frame(content, bg=self.palette["bg"])
        left_column.grid(row=0, column=0, sticky="nsew", padx=(0, 14))
        left_column.grid_rowconfigure(1, weight=1)

        control_panel = self._create_card(left_column, self.palette["surface"])
        control_panel.grid(row=0, column=0, sticky="ew")

        status_panel = self._create_card(left_column, self.palette["surface_alt"])
        status_panel.grid(row=1, column=0, sticky="nsew", pady=(14, 0))

        result_panel = self._create_card(content, self.palette["surface"])
        result_panel.grid(row=0, column=1, sticky="nsew")

        self._build_control_panel(control_panel)
        self._build_status_panel(status_panel)
        self._build_result_panel(result_panel)

        footer = self._create_card(outer, self.palette["surface_alt"])
        footer.pack(fill="x", pady=(18, 0))
        self._create_status_strip(footer, self.status_var).pack(fill="x")
        self._create_status_strip(footer, self.mq_var).pack(fill="x", pady=(10, 0))

    def _build_control_panel(self, parent):
        self._create_section_header(
            parent,
            "探测控制",
            "选择主机信息模块，执行手动探测或查看实时日志状态。",
        ).pack(fill="x")

        meta_row = tk.Frame(parent, bg=self.palette["surface"])
        meta_row.pack(fill="x", pady=(18, 0))
        meta_row.grid_columnconfigure(0, weight=1)
        meta_row.grid_columnconfigure(1, weight=1)

        self.permission_badge = self._create_info_block(meta_row, "权限状态", self.permission_var)
        self.permission_badge.grid(row=0, column=0, sticky="nsew", padx=(0, 6))
        self._create_info_block(meta_row, "运行环境", self.runtime_var).grid(
            row=0,
            column=1,
            sticky="nsew",
            padx=(6, 0),
        )

        toggle_shell = self._create_soft_panel(parent)
        toggle_shell.pack(fill="x", pady=(14, 0))
        tk.Label(
            toggle_shell,
            text="调试与观测",
            bg=self.palette["surface_alt"],
            fg=self.palette["text_soft"],
            font=("Consolas", 10, "bold"),
            anchor="w",
        ).pack(anchor="w")
        debug_toggle = tk.Checkbutton(
            toggle_shell,
            text="打开控制台日志输出",
            variable=self.log_enabled_var,
            command=self.toggle_logging,
            bg=self.palette["surface_alt"],
            fg=self.palette["text"],
            activebackground=self.palette["surface_alt"],
            activeforeground=self.palette["primary_alt"],
            selectcolor=self.palette["surface_alt"],
            font=("Microsoft YaHei UI", 11),
            anchor="w",
            padx=2,
            pady=8,
            highlightthickness=0,
            bd=0,
        )
        debug_toggle.pack(fill="x", pady=(8, 0))

        option_shell = self._create_soft_panel(parent)
        option_shell.pack(fill="x", pady=(14, 0))
        tk.Label(
            option_shell,
            text="探测模块",
            bg=self.palette["surface_alt"],
            fg=self.palette["text_soft"],
            font=("Consolas", 10, "bold"),
            anchor="w",
        ).grid(row=0, column=0, columnspan=2, sticky="w")
        option_shell.grid_columnconfigure(0, weight=1)
        option_shell.grid_columnconfigure(1, weight=1)

        for index, key in enumerate(DEFAULT_PROBE_ITEMS):
            card = tk.Frame(
                option_shell,
                bg=self.palette["surface_panel"],
                highlightbackground=self.palette["border"],
                highlightthickness=1,
                padx=10,
                pady=8,
            )
            row = (index // 2) + 1
            column = index % 2
            pad_x = (0, 6) if column == 0 else (6, 0)
            card.grid(row=row, column=column, sticky="nsew", padx=pad_x, pady=8)

            checkbox = tk.Checkbutton(
                card,
                text=self.option_labels[key],
                variable=self.option_vars[key],
                bg=self.palette["surface_panel"],
                fg=self.palette["text"],
                activebackground=self.palette["surface_panel"],
                activeforeground=self.palette["primary_alt"],
                selectcolor=self.palette["surface_panel"],
                font=("Microsoft YaHei UI", 11),
                anchor="w",
                padx=2,
                pady=4,
                highlightthickness=0,
                bd=0,
            )
            checkbox.pack(fill="x")

        button_bar = tk.Frame(parent, bg=self.palette["surface"])
        button_bar.pack(fill="x", pady=(18, 0))
        button_bar.grid_columnconfigure(0, weight=1)
        button_bar.grid_columnconfigure(1, weight=1)

        ttk.Button(
            button_bar,
            text="一键探测",
            style="Primary.TButton",
            command=self.run_probe,
        ).grid(row=0, column=0, sticky="ew", padx=(0, 6))

        ttk.Button(
            button_bar,
            text="清空结果",
            style="Secondary.TButton",
            command=self.clear_result,
        ).grid(row=0, column=1, sticky="ew", padx=(6, 0))

    def _build_status_panel(self, parent):
        self._create_section_header(
            parent,
            "实时状态",
            "同步查看启动上报、消息总线和监听线程的运行概况。",
        ).pack(fill="x")

        self._create_info_block(parent, "消息总线", self.mq_var).pack(fill="x", pady=(18, 0))
        self._create_info_block(parent, "资产监听", self.listener_var).pack(fill="x", pady=(12, 0))

        pulse_shell = self._create_soft_panel(parent)
        pulse_shell.pack(fill="both", expand=True, pady=(12, 0))
        tk.Label(
            pulse_shell,
            text="运行提示",
            bg=self.palette["surface_alt"],
            fg=self.palette["text_soft"],
            font=("Consolas", 10, "bold"),
            anchor="w",
        ).pack(anchor="w")
        for line in (
            "程序启动后自动采集主机基础信息并发送到 RabbitMQ。",
            "打开日志调试后，控制台才会输出每 3 秒一次的心跳信息。",
            "资产监听线程会实时消费 agent_<MAC>_queue 队列的资产指令。",
        ):
            tk.Label(
                pulse_shell,
                text="• " + line,
                bg=self.palette["surface_alt"],
                fg=self.palette["text_muted"],
                font=("Microsoft YaHei UI", 10),
                anchor="w",
                justify="left",
                wraplength=320,
            ).pack(anchor="w", pady=(12, 0))

    def _build_result_panel(self, parent):
        header = self._create_section_header(
            parent,
            "探测结果",
            "以裸 dict 形式展示当前主机的实时探测结果，便于核对与调试。",
        )
        header.pack(fill="x")

        summary_bar = tk.Frame(parent, bg=self.palette["surface"])
        summary_bar.pack(fill="x", pady=(18, 0))
        tk.Label(
            summary_bar,
            textvariable=self.result_summary_var,
            bg=self.palette["surface"],
            fg=self.palette["text_soft"],
            font=("Consolas", 10),
            anchor="w",
        ).pack(side="left")
        self._create_tag(summary_bar, "DICT 输出").pack(side="right")
        self._create_tag(summary_bar, "深灰蓝主题").pack(side="right", padx=(0, 10))

        self.result_text = scrolledtext.ScrolledText(
            parent,
            wrap=tk.WORD,
            bg=self.palette["result_bg"],
            fg=self.palette["text"],
            insertbackground=self.palette["primary_alt"],
            selectbackground=self.palette["primary_soft"],
            relief="flat",
            font=("Consolas", 11),
            padx=14,
            pady=14,
            highlightthickness=1,
            highlightbackground=self.palette["border"],
            highlightcolor=self.palette["primary"],
            bd=0,
        )
        self.result_text.pack(fill="both", expand=True, pady=(16, 0))
        self.result_text.configure(
            spacing1=2,
            spacing2=2,
            spacing3=2,
        )
        self.result_text.vbar.configure(
            troughcolor=self.palette["surface_alt"],
            bg=self.palette["surface_panel"],
            activebackground=self.palette["primary"],
        )
        self._set_result_text(
            {
                "tips": "程序启动后会自动采集并发送主机基础信息到 RabbitMQ。",
                "heartbeat": "打开日志调试后，心跳线程每 3 秒输出一次 {'mac_address': 'xx-xx-xx-xx-xx-xx'}。",
                "theme": "GUI 已统一为深灰蓝科技风。",
            }
        )

    def _set_permission_status(self):
        if self._is_admin():
            self.permission_var.set("管理员权限")
        else:
            self.permission_var.set("标准用户权限")

        if self.permission_badge:
            self.permission_badge.configure(
                highlightbackground=self.palette["primary"] if self._is_admin() else self.palette["border"],
            )

    def _get_selected_items(self):
        selected = []
        for key, variable in self.option_vars.items():
            if variable.get():
                selected.append(key)
        return selected

    def _set_result_text(self, result):
        result_text = pprint.pformat(result, width=100, sort_dicts=False)
        self.result_text.configure(state="normal")
        self.result_text.delete("1.0", tk.END)
        self.result_text.insert(tk.END, result_text)
        self.result_text.configure(state="disabled")
        field_count = len(result) if isinstance(result, dict) else 0
        self.result_summary_var.set(
            "字段数 {0}  |  最近刷新 {1}".format(
                field_count,
                datetime.now().strftime("%H:%M:%S"),
            )
        )

    def startup_collect_and_publish(self):
        self.status_var.set("启动任务进行中: 正在采集并发送主机基础信息...")
        worker = threading.Thread(target=self._startup_worker, daemon=True)
        worker.start()

    def _startup_worker(self):
        try:
            payload = self.service.collect_and_publish_startup_info()
        except Exception as exc:
            self.logger.exception("Startup data collection or publish failed.")
            self.root.after(0, self._handle_startup_failure, exc)
            return

        self.root.after(0, self._handle_startup_success, payload)

    def _handle_startup_success(self, payload):
        self._set_result_text(payload)
        self.status_var.set("启动任务完成: 基础信息已采集。")
        self.mq_var.set("RabbitMQ 状态: 启动数据发送成功")

    def _handle_startup_failure(self, exc):
        error_text = "启动阶段发送失败: {0}".format(exc)
        self.status_var.set(error_text)
        self.mq_var.set("RabbitMQ 状态: 发送失败")
        self._set_result_text(
            {
                "startup_status": "failed",
                "mq_error": str(exc),
            }
        )

    def run_probe(self):
        selected_items = self._get_selected_items()
        if not selected_items:
            messagebox.showwarning("未选择项目", "请至少勾选一个探测项。")
            self.status_var.set("未执行: 没有选择任何探测项。")
            return

        self.status_var.set("正在探测，请稍候...")
        self.root.update_idletasks()

        try:
            result = self.service.collect_selected_info(selected_items)
        except Exception as exc:
            self.logger.exception("Manual probe failed.")
            messagebox.showerror("探测失败", str(exc))
            self.status_var.set("探测失败。")
            return

        self._set_result_text(result)
        self.status_var.set("探测完成，共返回 {0} 个字段。".format(len(result)))

    def clear_result(self):
        self._set_result_text({})
        self.status_var.set("结果已清空。")

    def toggle_logging(self):
        enabled = self.log_enabled_var.get()
        self.log_handler.set_enabled(enabled)
        self.status_var.set("日志调试已开启。" if enabled else "日志调试已关闭。")
        self.logger.info("Console logging toggled: %s", enabled)

    def on_close(self):
        self.logger.info("Application is shutting down.")
        self.service.close()
        self.root.destroy()

    def _create_card(self, parent, bg, padx=18, pady=18):
        return tk.Frame(
            parent,
            bg=bg,
            highlightbackground=self.palette["border"],
            highlightthickness=1,
            padx=padx,
            pady=pady,
        )

    def _create_soft_panel(self, parent):
        return tk.Frame(
            parent,
            bg=self.palette["surface_alt"],
            highlightbackground=self.palette["border"],
            highlightthickness=1,
            padx=14,
            pady=14,
        )

    def _create_section_header(self, parent, title, subtitle):
        frame = tk.Frame(parent, bg=parent.cget("bg"))
        tk.Label(
            frame,
            text=title,
            bg=parent.cget("bg"),
            fg=self.palette["text"],
            font=("Microsoft YaHei UI", 16, "bold"),
            anchor="w",
        ).pack(anchor="w")
        tk.Label(
            frame,
            text=subtitle,
            bg=parent.cget("bg"),
            fg=self.palette["text_muted"],
            font=("Microsoft YaHei UI", 10),
            anchor="w",
            justify="left",
            wraplength=540,
        ).pack(anchor="w", pady=(6, 0))
        return frame

    def _create_metric_tile(self, parent, title, value_var):
        tile = tk.Frame(
            parent,
            bg=self.palette["surface_alt"],
            highlightbackground=self.palette["border"],
            highlightthickness=1,
            padx=14,
            pady=12,
        )
        tk.Label(
            tile,
            text=title,
            bg=self.palette["surface_alt"],
            fg=self.palette["text_soft"],
            font=("Consolas", 10, "bold"),
            anchor="w",
        ).pack(anchor="w")
        tk.Label(
            tile,
            textvariable=value_var,
            bg=self.palette["surface_alt"],
            fg=self.palette["text"],
            font=("Microsoft YaHei UI", 11),
            anchor="w",
            justify="left",
            wraplength=340,
        ).pack(anchor="w", pady=(8, 0))
        return tile

    def _create_info_block(self, parent, title, value_var):
        block = tk.Frame(
            parent,
            bg=self.palette["surface_alt"],
            highlightbackground=self.palette["border"],
            highlightthickness=1,
            padx=14,
            pady=12,
        )
        tk.Label(
            block,
            text=title,
            bg=self.palette["surface_alt"],
            fg=self.palette["text_soft"],
            font=("Consolas", 10, "bold"),
            anchor="w",
        ).pack(anchor="w")
        tk.Label(
            block,
            textvariable=value_var,
            bg=self.palette["surface_alt"],
            fg=self.palette["text"],
            font=("Microsoft YaHei UI", 11),
            anchor="w",
            justify="left",
            wraplength=320,
        ).pack(anchor="w", pady=(8, 0))
        return block

    def _create_tag(self, parent, text):
        return tk.Label(
            parent,
            text=text,
            bg=self.palette["primary_soft"],
            fg=self.palette["primary_alt"],
            font=("Microsoft YaHei UI", 9, "bold"),
            padx=10,
            pady=5,
        )

    def _create_status_strip(self, parent, value_var):
        strip = tk.Frame(
            parent,
            bg=self.palette["surface_alt"],
            highlightbackground=self.palette["border"],
            highlightthickness=1,
            padx=14,
            pady=10,
        )
        tk.Label(
            strip,
            textvariable=value_var,
            bg=self.palette["surface_alt"],
            fg=self.palette["success"],
            font=("Consolas", 10),
            anchor="w",
            justify="left",
        ).pack(fill="x")
        return strip

    @staticmethod
    def _is_admin():
        try:
            return bool(ctypes.windll.shell32.IsUserAnAdmin())
        except Exception:
            return False


class ApplicationBootstrap:
    def __init__(self):
        self.log_handler, self.logger = self._configure_logging()
        self.collector = HostInfoCollector(self.logger)
        self.config = RabbitMQConfig()
        self.publisher = RabbitMQPublisher(self.config, self.logger)
        self.heartbeat_service = HeartbeatService(self.collector, self.config, self.logger)
        self.asset_listener_service = AssetProbeListenerService(
            self.collector,
            self.config,
            self.logger,
        )
        self.service = HostInspectorService(
            self.collector,
            self.publisher,
            self.heartbeat_service,
            self.asset_listener_service,
            self.logger,
        )

    def run(self):
        root = tk.Tk()
        HostInspectorApp(root, self.service, self.logger, self.log_handler)
        root.mainloop()

    @staticmethod
    def _configure_logging():
        if hasattr(sys.stdout, "reconfigure"):
            sys.stdout.reconfigure(encoding="utf-8")
        if hasattr(sys.stderr, "reconfigure"):
            sys.stderr.reconfigure(encoding="utf-8")

        logger = logging.getLogger("host-inspector")
        logger.setLevel(logging.INFO)
        logger.propagate = False
        logger.handlers.clear()

        console_handler = ToggleableConsoleHandler(stream=sys.stdout, enabled=False)
        console_handler.setLevel(logging.INFO)
        console_handler.setFormatter(
            logging.Formatter("%(asctime)s [%(levelname)s] %(name)s - %(message)s")
        )
        logger.addHandler(console_handler)
        return console_handler, logger


if __name__ == "__main__":
    ApplicationBootstrap().run()
