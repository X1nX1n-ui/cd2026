with open(r"D:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "r", encoding="utf-8", errors="replace") as f:
    text = f.read()

# Comprehensive replacement mapping based on all identified garbled patterns
fixes = [
    # General: period and colon after Chinese
    ("自动刷新\ufffd/p>", "自动刷新。</p>"),
    ("系统名\ufffd autocomplete", "系统名称\" autocomplete"),
    ("全部状\ufffd/option>", "全部状态</option>"),
    ("不刷\ufffd/option>", "不刷新</option>"),
    ("1\ufffd/option>", "1秒</option>"),
    ("3\ufffd/option>", "3秒</option>"),
    ("5\ufffd/option>", "5秒</option>"),
    ("手动刷新模\ufffd/div>", "手动刷新模式</div>"),
    
    # Risk levels
    ('"高风\ufffd,', '"高风险",'),
    ('"中风\ufffd,', '"中风险",'),
    ('"低风\ufffd,', '"低风险",'),
    ('"无风\ufffd', '"无风险"'),
    ('"\ufffd: "high"', '"高": "high"'),
    ('"高风\ufffd: "high"', '"高风险": "high"'),
    ('"\ufffd: "medium"', '"中": "medium"'),
    ('"中风\ufffd: "medium"', '"中风险": "medium"'),
    ('"\ufffd: "low"', '"低": "low"'),
    ('"低风\ufffd: "low"', '"低风险": "low"'),
    ('"\ufffd: "safe"', '"无": "safe"'),
    ('"无风\ufffd: "safe"', '"无风险": "safe"'),
    
    # Table headers
    ('title: "主机\ufffd, minWidth', 'title: "主机名", minWidth'),
    ('title: "状\ufffd, width', 'title: "状态", width'),
    
    # Probe dialog
    ("发起资产探\ufffd);", "发起资产探测\");"),
    ("一个探测模\ufffd);", "一个探测模块\");"),
    ("指令发送失\ufffd);", "指令发送失败\");"),
    ("未命名主\ufffd) + '</div>'", "未命名主机\") + '</div>'"),
    ("<span>MAC\ufffd + PlatformUti", "<span>MAC：\" + PlatformUti"),
    ("<span>主IP\ufffd + PlatformUti", "<span>主IP：\" + PlatformUti"),
    ("影子账\ufffd)", "影子账号\")"),
    ("已安装软\ufffd)", "已安装软件\")"),
    ("发送探测指\ufffd/button>'", "发送探测指令</button>'"),
    
    # Auto refresh
    ('"当前为手动刷新模\ufffd;', '"当前为手动刷新模式";'),
    ('"列表\ufffd" + seconds +', '"列表每\" + seconds +'),
    ('" 秒自动刷新一\ufffd;', '" 秒自动刷新一次";'),
    
    # Detail items
    ('rDetailItem("主机\ufffd, host.hostnam', 'rDetailItem("主机名\", host.hostnam'),
    ('rDetailItem("状\ufffd, PlatformUtil', 'rDetailItem("状态\", PlatformUtil'),
    ('rDetailItem("总内\ufffd, host.memoryT', 'rDetailItem("总内存\", host.memoryT'),
    ('"最后上报时\ufffd, PlatformUtil', '"最后上报时间\", PlatformUtil'),
    ('未命名主\ufffd) + \'</h2>\'', '未命名主机\") + \'</h2>\''),
    
    # Asset dialog
    ("该资产类\ufffd);", "该资产类型\");"),
    ("<p>MAC\ufffd + PlatformUti", "<p>MAC：\" + PlatformUti"),
    ("并排对比\ufffd/span>'", "并排对比。</span>'"),
    ("共找\ufffd' + snapshots.", "共找到\" + snapshots."),
    ("' \ufffd + PlatformUti", "' 条\" + PlatformUti"),
    ("并排对比差异\ufffd/span>'", "并排对比差异。</span>'"),
    ("退出对\ufffd/button>'", "退出对比</button>'"),
    ("准备开始分\ufffd..</div>'", "准备开始分析...</div>'"),
    ("查看详\ufffd/div>')", "查看详情</div>')"),
    ('"最\ufffd/span>\'', '"最新</span>\''),
    ('"更新时间\ufffd + PlatformUti', '"更新时间：\" + PlatformUti'),
    ('"记录时间\ufffd + PlatformUti', '"记录时间：\" + PlatformUti'),
    ('"记录编号\ufffd + PlatformUti', '"记录编号：\" + PlatformUti'),
    ('"准备开始分\ufffd..";', '"准备开始分析...";'),
    ('该条记\ufffd/div>\';', '该条记录</div>\';'),
    ('"分析\ufffd..";', '"分析中...";'),
    ('详细内容\ufffd/p>\'', '详细内容。</p>\''),
    ('<span>更新时间\ufffd + PlatformUti', '<span>更新时间：\" + PlatformUti'),
    ('（当前显\ufffd\' + config.fil', '（当前显示\" + config.fil'),
    ('+ ') \ufffd"', '+ ') 条"'),
    ("' + items.leng", "' + items.leng"),  # skip this, already correct
    ("\ufffd\\' + items.leng", "\" + items.leng"),  # complex, handle differently
    ("th + ' \ufffd/span>'", "th + ' 条</span>'"),
    ("上报内\ufffd/span>'", "上报内容</span>'"),
    ("<span>\ufffd' + (index + 1", "<span>\" + (index + 1"),
    (") + ' \ufffd/span></header", ") + ' 条</span></header"),
    
    # Field labels
    ('SummaryChip("服务\ufffd, firstDefined', 'SummaryChip("服务名\", firstDefined'),
    ('SummaryChip("状\ufffd, firstDefined', 'SummaryChip("状态\", firstDefined'),
    ('SummaryChip("进程\ufffd, firstDefined', 'SummaryChip("进程名\", firstDefined'),
    ('SummaryChip("可执行路\ufffd, firstDefined', 'SummaryChip("可执行路径\", firstDefined'),
    ('SummaryChip("程序\ufffd, firstDefined', 'SummaryChip("程序名\", firstDefined'),
    ('SummaryChip("发布\ufffd, firstDefined', 'SummaryChip("发布者\", firstDefined'),
    ('SummaryChip("账号\ufffd, firstDefined', 'SummaryChip("账号名\", firstDefined'),
    ('SummaryChip("启用状\ufffd, firstDefined', 'SummaryChip("启用状态\", firstDefined'),
    ('SummaryChip("最后登\ufffd, firstDefined', 'SummaryChip("最后登录\", firstDefined'),
    ("Html(label) + '\ufffd + PlatformUti", "Html(label) + '\ufffd + PlatformUti"),  # skip
    
    # Risk stat cards
    ('iskStatCard("高风\ufffd + assetLabel,', 'iskStatCard("高风险\" + assetLabel,'),
    ('iskStatCard("中风\ufffd + assetLabel,', 'iskStatCard("中风险\" + assetLabel,'),
    ('iskStatCard("低风\ufffd + assetLabel,', 'iskStatCard("低风险\" + assetLabel,'),
    ('iskStatCard("无风\ufffd + assetLabel,', 'iskStatCard("无风险\" + assetLabel,'),
    
    # Risk parsing
    ('.replace(/[\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd\ufffd]/g,', '.replace(/[：，。；、？！—]/g,'),
    ('zed.indexOf("高风\ufffd) >= 0 || norm', 'zed.indexOf("高风险") >= 0 || norm'),
    ('alized === "\ufffd") {', 'alized === "高") {'),
    ('zed.indexOf("中风\ufffd) >= 0 || norm', 'zed.indexOf("中风险") >= 0 || norm'),
    ('alized === "\ufffd") {', 'alized === "中") {'),
    ('zed.indexOf("低风\ufffd) >= 0 || norm', 'zed.indexOf("低风险") >= 0 || norm'),
    ('alized === "\ufffd") {', 'alized === "低") {'),
    ('zed.indexOf("无风\ufffd) >= 0 || norm', 'zed.indexOf("无风险") >= 0 || norm'),
    ('alized === "\ufffd") {', 'alized === "无") {'),
    ('ext.indexOf("高风\ufffd) >= 0 || resu', 'ext.indexOf("高风险") >= 0 || resu'),
    ('indexOf("风险等级:\ufffd) >= 0)', 'indexOf("风险等级:高") >= 0)'),
    ('ext.indexOf("中风\ufffd) >= 0 || resu', 'ext.indexOf("中风险") >= 0 || resu'),
    ('indexOf("风险等级:\ufffd) >= 0)', 'indexOf("风险等级:中") >= 0)'),
    ('ext.indexOf("低风\ufffd) >= 0 || resu', 'ext.indexOf("低风险") >= 0 || resu'),
    ('indexOf("风险等级:\ufffd) >= 0)', 'indexOf("风险等级:低") >= 0)'),
    ('ext.indexOf("无风\ufffd) >= 0 || resu', 'ext.indexOf("无风险") >= 0 || resu'),
    ('indexOf("风险等级:\ufffd) >= 0)', 'indexOf("风险等级:无") >= 0)'),
    
    # Field name mappings
    ('riskKey] || "未分\ufffd;', 'riskKey] || "未分级\";'),
    ('name: "账号\ufffd,', 'name: "账号名\",'),
    ('enabled: "启用状\ufffd,', 'enabled: "启用状态\",'),
    ('Enabled: "启用状\ufffd,', 'Enabled: "启用状态\",'),
    ('t_logon: "最后登录时\ufffd,', 't_logon: "最后登录时间\",'),
    ('State: "状\ufffd,', 'State: "状态\",'),
    ('ablePath: "可执行路\ufffd,', 'ablePath: "可执行路径\",'),
    ('ommandLine: "命令\ufffd,', 'ommandLine: "命令行\",'),
    ('Publisher: "发布\ufffd,', 'Publisher: "发布者\",'),
    ('state: "状\ufffd,', 'state: "状态\",'),
    ('ble_path: "可执行路\ufffd,', 'ble_path: "可执行路径\",'),
    ('mmand_line: "命令\ufffd,', 'mmand_line: "命令行\",'),
    ('publisher: "发布\ufffd,', 'publisher: "发布者\",'),
    
    # Advice/analysis
    ('"建议\ufffd) >= 0))', '"建议：") >= 0))'),
    ('风险原因[\ufffd]\\\\s*', '风险原因[：]\\\\s*'),
    ('加固建议[\ufffd]|建议[\ufffd]|$)', '加固建议[：]|建议[：]|$)'),
    ('加固建议|建议)[\ufffd]\\\\s*', '加固建议|建议)[：]\\\\s*'),
    
    # Host form
    ('"请输\ufffdMAC 地址"', '"请输入MAC 地址"'),
    ('renderInput("主机\ufffd, "hostname"', 'renderInput("主机名\", "hostname\"'),
    ('"请输入逻辑核心\ufffd, "number"', '"请输入逻辑核心数\", "number\"'),
    ('renderInput("总内\ufffd, "memoryTotal"', 'renderInput("总内存\", "memoryTotal\"'),
    ('renderSelect("状\ufffd, "status"', 'renderSelect("状态\", "status\"'),
    ('"请输\ufffd + label)', '"请输入\" + label)'),
    
    # Export
    ('"导出组件未就\ufffd);', '"导出组件未就绪\");'),
    ('未命名主\ufffd) + \'</strong>\'', '未命名主机\") + \'</strong>\''),
    ('的最\ufffd<strong>\'', '的最新<strong>\''),
    ('暴露完整内容\ufffd/p>\'', '暴露完整内容。</p>\''),
    ('适合\ufffdExcel 打开查看和筛\ufffd', '适合用Excel 打开查看和筛选'),
    ('进一步分\ufffd/em>\'', '进一步分析</em>\''),
    ('排版清晰规\ufffd/em>\'', '排版清晰规范</em>\''),
    ('开始导\ufffd/button>\'', '开始导出</button>\''),
    ('"处理\ufffd..");', '"处理中...");'),
    ('"??????????????"', '"导出失败，请重试"'),
    ('hRiskCount + \' \ufffd/span>，请重点关注\'', 'hRiskCount + \' 条</span>，请重点关注\''),
    ("'未发现高风险\ufffd;", "'未发现高风险项\";"),
    ("+ '</strong> \ufffd/p>');", "+ '</strong> 条</p>');"),
    ("'您导出的\ufffd<strong>' + Pl", "'您导出的是<strong>' + Pl"),
    ("+ '</strong> 条记\ufffd/p>'", "+ '</strong> 条记录</p>'"),
    ('"知道\ufffd/button>\'', '"知道了</button>\''),
    ('"导出失败\ufffd + (task.messa', '"导出失败：\" + (task.messa'),
    ('"未知错误") + "\ufffd<br>要重新尝试导出吗\ufffd,', '"未知错误") + "\"。<br>要重新尝试导出吗？\",'),
]

count = 0
for old, new in fixes:
    if old in text:
        text = text.replace(old, new)
        count += 1

print("Applied %d fixes" % count)
print("Remaining replacement chars: %d" % text.count('\ufffd'))

with open(r"D:\3\code\java\src\main\resources\static\pages\assets\hosts.html", "w", encoding="utf-8") as f:
    f.write(text)

print("File saved")
