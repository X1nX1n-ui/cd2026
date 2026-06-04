import ctypes
import logging
import pprint
import sys
import threading
import tkinter as tk
from tkinter import messagebox
from tkinter import scrolledtext
from tkinter import ttk

from config import APP_TITLE
from config import DEFAULT_PROBE_ITEMS
from config import RabbitMQConfig
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
            print("Heartbeat:", payload, flush=True)

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


class HostInspectorService:
    def __init__(self, collector, publisher, heartbeat_service, logger):
        self.collector = collector
        self.publisher = publisher
        self.heartbeat_service = heartbeat_service
        self.logger = logger

    def start(self):
        self.heartbeat_service.start()

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
        self.heartbeat_service.stop()
        self.publisher.close()


class HostInspectorApp:
    def __init__(self, root, service, logger, log_handler):
        self.root = root
        self.service = service
        self.logger = logger
        self.log_handler = log_handler

        self.root.title(APP_TITLE)
        self.root.geometry("1180x760")
        self.root.minsize(1020, 680)
        self.root.configure(bg="#06111d")
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

        self._configure_style()
        self._build_ui()
        self._set_permission_status()
        self.root.after(100, self.service.start)
        self.root.after(200, self.startup_collect_and_publish)

    def _configure_style(self):
        style = ttk.Style()
        style.theme_use("clam")

        style.configure("Sci.TFrame", background="#06111d")
        style.configure(
            "Panel.TFrame",
            background="#0d1b2a",
            borderwidth=1,
            relief="solid",
        )
        style.configure(
            "Title.TLabel",
            background="#06111d",
            foreground="#7df9ff",
            font=("Consolas", 24, "bold"),
        )
        style.configure(
            "SubTitle.TLabel",
            background="#06111d",
            foreground="#9fc5e8",
            font=("Microsoft YaHei UI", 10),
        )
        style.configure(
            "PanelTitle.TLabel",
            background="#0d1b2a",
            foreground="#7df9ff",
            font=("Microsoft YaHei UI", 13, "bold"),
        )
        style.configure(
            "Info.TLabel",
            background="#0d1b2a",
            foreground="#d9f7ff",
            font=("Microsoft YaHei UI", 10),
        )
        style.configure(
            "Status.TLabel",
            background="#06111d",
            foreground="#8ef6d0",
            font=("Consolas", 10),
        )
        style.configure(
            "Primary.TButton",
            background="#00b4d8",
            foreground="#02131f",
            font=("Microsoft YaHei UI", 10, "bold"),
            padding=(16, 10),
            borderwidth=0,
        )
        style.map(
            "Primary.TButton",
            background=[("active", "#7df9ff"), ("pressed", "#48cae4")],
            foreground=[("active", "#02131f")],
        )
        style.configure(
            "Secondary.TButton",
            background="#1d3557",
            foreground="#d9f7ff",
            font=("Microsoft YaHei UI", 10),
            padding=(16, 10),
            borderwidth=0,
        )
        style.map(
            "Secondary.TButton",
            background=[("active", "#274c77"), ("pressed", "#1d3557")],
        )

    def _build_ui(self):
        outer = ttk.Frame(self.root, style="Sci.TFrame", padding=20)
        outer.pack(fill="both", expand=True)

        header = ttk.Frame(outer, style="Sci.TFrame")
        header.pack(fill="x")

        ttk.Label(header, text=APP_TITLE, style="Title.TLabel").pack(anchor="w")
        ttk.Label(
            header,
            text="启动时自动采集并发送主机基础信息，心跳线程每 3 秒上报一次 MAC 地址。",
            style="SubTitle.TLabel",
        ).pack(anchor="w", pady=(6, 12))

        neon_line = tk.Frame(header, bg="#00e5ff", height=2)
        neon_line.pack(fill="x", pady=(0, 14))

        content = ttk.Frame(outer, style="Sci.TFrame")
        content.pack(fill="both", expand=True)
        content.columnconfigure(0, weight=3)
        content.columnconfigure(1, weight=5)
        content.rowconfigure(0, weight=1)

        control_panel = ttk.Frame(content, style="Panel.TFrame", padding=18)
        control_panel.grid(row=0, column=0, sticky="nsew", padx=(0, 12))

        result_panel = ttk.Frame(content, style="Panel.TFrame", padding=18)
        result_panel.grid(row=0, column=1, sticky="nsew")
        result_panel.rowconfigure(1, weight=1)
        result_panel.columnconfigure(0, weight=1)

        self._build_control_panel(control_panel)
        self._build_result_panel(result_panel)

        footer = ttk.Frame(outer, style="Sci.TFrame")
        footer.pack(fill="x", pady=(12, 0))

        ttk.Label(footer, textvariable=self.status_var, style="Status.TLabel").pack(anchor="w")
        ttk.Label(footer, textvariable=self.mq_var, style="Status.TLabel").pack(anchor="w", pady=(4, 0))

    def _build_control_panel(self, parent):
        ttk.Label(parent, text="探测选项", style="PanelTitle.TLabel").pack(anchor="w")
        ttk.Label(
            parent,
            text="勾选需要的模块后，可以手动重新探测。",
            style="Info.TLabel",
        ).pack(anchor="w", pady=(8, 10))

        permission_label = tk.Label(
            parent,
            textvariable=self.permission_var,
            bg="#0d1b2a",
            fg="#8ef6d0",
            font=("Consolas", 10, "bold"),
            anchor="w",
            justify="left",
            padx=12,
            pady=10,
            relief="solid",
            bd=1,
        )
        permission_label.pack(fill="x", pady=(0, 10))

        runtime_label = tk.Label(
            parent,
            text=f"运行时: Python {sys.version_info.major}.{sys.version_info.minor}",
            bg="#0d1b2a",
            fg="#7df9ff",
            font=("Consolas", 10),
            anchor="w",
            justify="left",
            padx=12,
            pady=10,
            relief="solid",
            bd=1,
        )
        runtime_label.pack(fill="x", pady=(0, 10))

        debug_toggle = tk.Checkbutton(
            parent,
            text="日志调试开关",
            variable=self.log_enabled_var,
            command=self.toggle_logging,
            bg="#0d1b2a",
            fg="#d9f7ff",
            activebackground="#12344f",
            activeforeground="#7df9ff",
            selectcolor="#06111d",
            font=("Microsoft YaHei UI", 11),
            anchor="w",
            padx=12,
            pady=8,
            highlightthickness=0,
            bd=0,
        )
        debug_toggle.pack(fill="x", pady=(0, 16))

        checkbox_frame = tk.Frame(
            parent,
            bg="#0d1b2a",
            highlightbackground="#12344f",
            highlightthickness=1,
        )
        checkbox_frame.pack(fill="x", pady=(0, 18))

        for key in DEFAULT_PROBE_ITEMS:
            checkbox = tk.Checkbutton(
                checkbox_frame,
                text=self.option_labels[key],
                variable=self.option_vars[key],
                bg="#0d1b2a",
                fg="#d9f7ff",
                activebackground="#12344f",
                activeforeground="#7df9ff",
                selectcolor="#06111d",
                font=("Microsoft YaHei UI", 11),
                anchor="w",
                padx=12,
                pady=8,
                highlightthickness=0,
                bd=0,
            )
            checkbox.pack(fill="x", anchor="w")

        button_bar = ttk.Frame(parent, style="Panel.TFrame")
        button_bar.pack(fill="x")

        ttk.Button(
            button_bar,
            text="一键探测",
            style="Primary.TButton",
            command=self.run_probe,
        ).pack(fill="x", pady=(0, 10))

        ttk.Button(
            button_bar,
            text="清空结果",
            style="Secondary.TButton",
            command=self.clear_result,
        ).pack(fill="x")

    def _build_result_panel(self, parent):
        ttk.Label(parent, text="数据显示模块", style="PanelTitle.TLabel").grid(row=0, column=0, sticky="w")

        self.result_text = scrolledtext.ScrolledText(
            parent,
            wrap=tk.WORD,
            bg="#02131f",
            fg="#b8f2ff",
            insertbackground="#7df9ff",
            selectbackground="#144d6f",
            relief="flat",
            font=("Consolas", 11),
            padx=14,
            pady=14,
        )
        self.result_text.grid(row=1, column=0, sticky="nsew", pady=(12, 0))
        self._set_result_text(
            {
                "tips": "程序启动后会自动采集并发送主机基础信息到 RabbitMQ。",
                "heartbeat": "心跳线程每 3 秒发送一次 {'mac_address': 'xx-xx-xx-xx-xx-xx'}。",
            }
        )

    def _set_permission_status(self):
        if self._is_admin():
            self.permission_var.set("当前权限级别: Administrator")
        else:
            self.permission_var.set("当前权限级别: Standard User")

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
        self.service = HostInspectorService(
            self.collector,
            self.publisher,
            self.heartbeat_service,
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
