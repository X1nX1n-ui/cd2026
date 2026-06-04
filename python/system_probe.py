import ctypes
import os
import platform
import socket
import uuid

try:
    import winreg
except ImportError:
    winreg = None


class HostInfoCollector:
    def __init__(self, logger=None):
        self.logger = logger
        self.probe_map = {
            "hostname": self.get_hostname,
            "ip_address": self.get_ip_address,
            "mac_address": self.get_mac_address,
            "os_info": self.get_os_info,
            "cpu_info": self.get_cpu_info,
            "memory_info": self.get_memory_info,
        }

    def collect_selected(self, selected_items):
        result = {}
        for item in selected_items:
            probe_method = self.probe_map[item]
            result.update(self._safe_probe(item, probe_method))
        return result

    def collect_startup_payload(self):
        startup_items = (
            "hostname",
            "ip_address",
            "mac_address",
            "os_info",
            "cpu_info",
            "memory_info",
        )
        return self.collect_selected(startup_items)

    def _safe_probe(self, item_name, probe_method):
        try:
            return probe_method()
        except Exception as exc:
            if self.logger:
                self.logger.exception("探测模块 %s 失败。", item_name)
            return {
                "error_{0}".format(item_name): str(exc),
            }

    @staticmethod
    def get_hostname():
        return {
            "hostname": socket.gethostname(),
        }

    @staticmethod
    def get_ip_address():
        ip_address = "127.0.0.1"
        probe_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        try:
            probe_socket.connect(("8.8.8.8", 80))
            ip_address = probe_socket.getsockname()[0]
        except Exception:
            ip_address = socket.gethostbyname(socket.gethostname())
        finally:
            probe_socket.close()

        return {
            "ip_address": ip_address,
        }

    @staticmethod
    def get_mac_address():
        mac_num = uuid.getnode()
        mac_hex = "{0:012X}".format(mac_num)
        return {
            "mac_address": "-".join(mac_hex[index:index + 2] for index in range(0, 12, 2)),
        }

    def get_os_info(self):
        system_type = platform.system()
        os_name = platform.platform()
        os_version = platform.version()
        os_detail = platform.release()
        os_bitness = self.get_os_bitness()

        if system_type == "Windows":
            product_name = self.read_windows_registry(
                "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                "ProductName",
            )
            build_number = self.read_windows_registry(
                "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                "CurrentBuildNumber",
            )
            display_version = self.read_windows_registry(
                "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                "DisplayVersion",
            )
            release_id = self.read_windows_registry(
                "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                "ReleaseId",
            )

            if product_name:
                os_name = product_name
                os_detail = product_name

            version_parts = []
            if display_version:
                version_parts.append(display_version)
            elif release_id:
                version_parts.append(release_id)
            else:
                version_parts.append(platform.version())

            if build_number:
                version_parts.append("Build {0}".format(build_number))

            os_version = " | ".join(version_parts)

        return {
            "os_type": system_type,
            "os_name": os_name,
            "os_version": os_version,
            "os_bitness": os_bitness,
            "os_detail": os_detail,
        }

    def get_cpu_info(self):
        cpu_name = platform.processor() or "Unknown CPU"
        if platform.system() == "Windows":
            registry_name = self.read_windows_registry(
                "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\0",
                "ProcessorNameString",
            )
            if registry_name:
                cpu_name = registry_name.strip()

        return {
            "cpu_name": cpu_name,
            "cpu_architecture": platform.machine(),
            "logical_cores": os.cpu_count(),
        }

    def get_memory_info(self):
        total_memory_bytes, available_memory_bytes = self.get_memory_status()
        return {
            "memory_total": self.format_bytes(total_memory_bytes),
            "memory_available": self.format_bytes(available_memory_bytes),
        }

    def get_memory_status(self):
        if platform.system() == "Windows":
            return self.get_windows_memory_status()

        total_bytes = None
        available_bytes = None
        if hasattr(os, "sysconf"):
            pages = os.sysconf("SC_PHYS_PAGES")
            page_size = os.sysconf("SC_PAGE_SIZE")
            avail_pages = os.sysconf("SC_AVPHYS_PAGES")
            total_bytes = pages * page_size
            available_bytes = avail_pages * page_size

        return total_bytes, available_bytes

    @staticmethod
    def get_windows_memory_status():
        class MEMORYSTATUSEX(ctypes.Structure):
            _fields_ = [
                ("dwLength", ctypes.c_ulong),
                ("dwMemoryLoad", ctypes.c_ulong),
                ("ullTotalPhys", ctypes.c_ulonglong),
                ("ullAvailPhys", ctypes.c_ulonglong),
                ("ullTotalPageFile", ctypes.c_ulonglong),
                ("ullAvailPageFile", ctypes.c_ulonglong),
                ("ullTotalVirtual", ctypes.c_ulonglong),
                ("ullAvailVirtual", ctypes.c_ulonglong),
                ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
            ]

        memory_status = MEMORYSTATUSEX()
        memory_status.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
        ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(memory_status))
        return memory_status.ullTotalPhys, memory_status.ullAvailPhys

    @staticmethod
    def get_os_bitness():
        if platform.system() == "Windows":
            return "64bit" if ctypes.sizeof(ctypes.c_void_p) == 8 or os.environ.get("PROCESSOR_ARCHITEW6432") else "32bit"
        return platform.architecture()[0]

    @staticmethod
    def format_bytes(size_in_bytes):
        if size_in_bytes is None:
            return "Unknown"

        units = ["B", "KB", "MB", "GB", "TB"]
        value = float(size_in_bytes)
        unit_index = 0

        while value >= 1024 and unit_index < len(units) - 1:
            value /= 1024.0
            unit_index += 1

        return "{0:.2f} {1}".format(value, units[unit_index])

    @staticmethod
    def read_windows_registry(sub_key, value_name, root=None):
        if winreg is None:
            return None

        root_key = root or winreg.HKEY_LOCAL_MACHINE
        try:
            registry_key = winreg.OpenKey(root_key, sub_key)
            value, _ = winreg.QueryValueEx(registry_key, value_name)
            winreg.CloseKey(registry_key)
            return value
        except Exception:
            return None
