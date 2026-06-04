# 主机信息探测 GUI 项目

这是一个基于 `tkinter` 的桌面工具，用来采集并展示主机的关键信息，并在启动时自动把基础信息发送到 RabbitMQ。

## 已实现功能

- 获取主机名
- 获取 IP 地址
- 获取 MAC 地址
- 获取操作系统信息
  - 操作系统类型
  - 操作系统名字
  - 操作系统版本号
  - 操作系统位数
  - 具体系统类型（例如 Windows 10 / Windows 11）
- 获取 CPU 信息
- 获取内存信息
- 程序启动时自动将基础主机信息发送到 RabbitMQ

## 界面说明

- 左侧：复选框选择本次需要探测的项目
- 左下：一键探测 / 清空结果
- 右侧：数据展示模块，结果以一层 `dict` 格式呈现
- 顶部：显示当前运行权限级别
- 控制台：输出采集、连接 MQ、发送消息等日志

## RabbitMQ 配置

- Host: `118.24.73.32`
- Port: `15333`
- VHost: `my_vhost`
- Username: `admin`
- Password: `123456`
- Exchange: `sysinfo_exchange`
- Routing Key: `sysinfo`

## 运行方式

建议使用 Python 3.10，在当前目录执行：

```bash
py -3.10 -m pip install -r requirements.txt
py -3.10 main.py
```

## 说明

- 当前版本使用面向对象结构，便于后续扩展更多消息类型或增加消息接收能力
- 在 Windows 下会优先读取注册表与系统接口，以拿到更具体的系统名称和 CPU 信息
