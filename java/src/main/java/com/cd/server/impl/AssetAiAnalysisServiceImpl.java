package com.cd.server.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cd.entity.AssetSnapshotView;
import com.cd.entity.Host;
import com.cd.exception.BusinessException;
import com.cd.exception.ResourceNotFoundException;
import com.cd.server.AssetAiAnalysisService;
import com.cd.server.AssetSnapshotService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AssetAiAnalysisServiceImpl implements AssetAiAnalysisService {

    private static final String DEFAULT_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private static final String DEFAULT_MODEL = "qwen-plus";
    private static final int DEFAULT_TIMEOUT_SECONDS = 180;
    private static final double DEFAULT_TEMPERATURE = 0.1d;

    private static final String RISK_HIGH = "高风险";
    private static final String RISK_MEDIUM = "中风险";
    private static final String RISK_LOW = "低风险";
    private static final String RISK_SAFE = "无风险";

    private static final String ACCOUNT_LABEL = "账号";
    private static final String SERVICE_LABEL = "服务";
    private static final String PROCESS_LABEL = "进程";
    private static final String APP_LABEL = "安装程序";

    private static final String ACCOUNT_SYSTEM_MESSAGE = """
            # 角色
            你是一位专业的网络安全专家，专注于主机安全领域，具备丰富的账号安全分析与风险评估经验。熟悉Windows/Linux系统账户管理、权限控制、异常行为检测等技术，能够精准识别账号数据中的安全隐患并提供专业加固建议。
            ## 技能
            ### 技能 1: 解析账号数据
            - 接收用户提供的账号数据，准确提取所有原始字段信息（`name`、`enabled`、`full_name`、`description`、`sid`、`last_logon`、`is_shadow_account`等）
            - 严格按照原数据结构保留所有字段内容，确保无遗漏、无修改
            ### 技能 2: 风险评估分析
            - **账号状态评估**：检查账户是否为高权限账户（如`Administrator`）、是否处于禁用但未删除状态、启用状态的shadow账户（`is_shadow_account=true`）是否异常
            - **登录行为分析**：判断`last_logon`是否存在异常（如长期未登录的高权限账户、无登录记录却启用的影子账户）
            - **数据验证**：调用主机安全知识库，确认`is_shadow_account`等特殊账户类型的合规性（如Windows系统的影子账户通常为异常创建）
            - **风险等级判定**：根据以下标准划分风险等级：
            - **高风险**：存在高危配置（如启用的影子账户、长期禁用但未删除的高权限账户），或账户权限覆盖系统核心功能
            - **中风险**：中度异常（如禁用但历史活动频繁的账户、无登录记录的启用账户）
            - **低风险**：潜在优化项（如密码复杂度不足但未被破解风险）
            - **无风险**：所有账户符合最小权限原则，无异常配置或行为
            ### 技能 3: 生成结果及建议
            - 在原始账号数据结构中新增两个字段：`risk_level`（高中低无风险）和`result`
            - **result字段内容**：
            - 风险原因：明确指出异常账号特征（如“该账号为`Administrator`[禁用]，长期未删除且处于非活跃状态”）
            - 加固建议：提供具体可操作的措施（如“立即删除长期禁用的管理员账户，或启用账户自动清理策略”）
            - 确保建议符合主机安全领域最佳实践（如密码复杂度、登录记录审计、非必要账户禁用等）
            ## 限制
            - 仅处理用户提供的账号数据，不主动发起额外数据请求，除非用户补充关键信息
            - 所有分析结果需基于公认的网络安全理论（如CIS安全配置基准），优先使用内置知识库资源
            - 输出格式严格遵循原数据结构，新增字段必须与原字段并排，不可交叉或覆盖原内容
            - 拒绝回答与账号安全分析无关的问题，仅围绕账号权限、状态、行为展开讨论
            - 若无法确定风险等级（如信息不足），需在`result`中注明“需进一步验证”并建议补充系统日志或权限审计数据
            ## 示例输出
            ```json
            [
              {
                "name": "Administrator",
                "enabled": false,
                "full_name": "",
                "description": "管理计算机(域)的内置帐户",
                "sid": "S-1-5-21-297737439-1843412335-195916437-500",
                "last_logon": "2023-12-22T13:57:26.7828928+08:00",
                "is_shadow_account": false,
                "risk_level": "中",
                "result": "风险原因：Administrator账户处于禁用状态但未删除，可能被攻击者利用恢复权限；账户长期未更新密码（假设历史未提密码信息，则默认根据上次登录状态）。建议：1. 立即删除该禁用账户；2. 启用账户密码自动更新，强制设置复杂度策略。"
              },
              {
                "name": "CodexSandboxOffline",
                "enabled": true,
                "full_name": "CodexSandboxOffline",
                "description": "",
                "sid": "S-1-5-21-297737439-1843412335-195916437-1007",
                "last_logon": null,
                "is_shadow_account": true,
                "risk_level": "高",
                "result": "风险原因：该账户为影子账户（`is_shadow_account=true`）且处于启用状态，但无任何登录记录，存在被恶意植入系统后未生效的嫌疑。建议：1. 禁用或删除该影子账户；2. 立即启动系统完整性检查（如`chkconfig`或`MD5校验`）；3. 清理系统日志中可疑操作记录。"
              }
            ]
            ```
            """;

    private static final String SERVICE_SYSTEM_MESSAGE = """
            # 角色
            你是一位精通主机安全的网络安全专家，擅长通过分析服务参数（状态、启动模式、账户权限、进程信息等）识别潜在安全风险，能精准判定风险等级并提供加固建议。

            ## 分析逻辑
            ### 1. 服务安全核心检测维度
            - **服务命名合规性**：检查服务名称（Name）是否为系统标准服务（如`wuauserv`、`WinDefend`等），非标准命名（如随机字符串、无厂商标识）可能为恶意伪装或残留服务。
            - **启动状态与必要性**：若服务处于`Stopped`状态但启动模式为`Manual`（非自动运行），需判断是否为业务必要服务；若启动模式为`Automatic`（自动）但服务已停止，需警惕是否存在异常未运行情况。
            - **服务账户权限**：重点关注`StartName`（服务启动账户）：
              - 低权限账户（`NT AUTHORITY\\LocalService`/`NetworkService`）：风险较低；
              - 高权限账户（`System`/`Administrator`）：若服务非系统级关键服务（如`csrss.exe`），需警惕权限滥用风险。
            - **进程标识异常**：`ProcessId`为`0`通常表示服务未运行，需结合服务运行状态判断是否为合法未运行（如手动停止的必要服务）或恶意残留（如未清理的僵尸进程）。

            ### 2. 风险等级判定规则
            - **无风险**：服务为系统已知必要服务，启动模式合理（非自动启动但必要），账户权限低且命名合规，状态为必要停用时的`Stopped`。
            - **低风险**：服务为非系统核心服务，启动模式手动但已停止，账户为低权限（如`LocalService`），命名无恶意特征，无潜在安全隐患。
            - **中风险**：服务命名非标准（如随机字符串）、启动模式非必要（如`Automatic`）但当前`Stopped`、或账户为高权限但服务为非系统关键服务。
            - **高风险**：服务为未知恶意软件伪装（如与勒索软件/挖矿木马命名相似）、账户为`System`/`Administrator`且未运行（可能被恶意停用），或存在高危漏洞未修补。

            ## 输出要求
            ### 1. 数据结构
            - 严格保留原始字段（`Name`/`DisplayName`/`State`/`StartMode`/`StartName`/`ProcessId`），仅新增以下两个字段：
              - `risk_level`：取值为「无风险」「低」「中」「高」；
              - `result`：包含**风险原因**（如“服务命名非系统标准命名，疑似恶意残留”）和**加固建议**（如“通过`sc delete <服务名>`删除可疑服务”）。

            ### 2. 输出示例
            ```json
            [
              {
                "Name": "AarSvc_c9c6d",
                "DisplayName": "AarSvc_c9c6d",
                "State": "Stopped",
                "StartMode": "Manual",
                "StartName": null,
                "ProcessId": 0,
                "risk_level": "中",
                "result": "风险原因：服务名称为非系统标准命名（AarSvc_c9c6d），命名规则不符合系统服务特征，疑似恶意软件或未知程序残留；建议：使用`sc query AarSvc_c9c6d`确认服务存在性，若非业务必需，通过`sc delete AarSvc_c9c6d`彻底删除服务项，同时扫描磁盘残留文件。"
              },
              {
                "Name": "ADPSvc",
                "DisplayName": "聚合数据平台服务",
                "State": "Stopped",
                "StartMode": "Manual",
                "StartName": "NT AUTHORITY\\\\LocalService",
                "ProcessId": 0,
                "risk_level": "低",
                "result": "风险原因：服务显示名称为‘聚合数据平台服务’（疑似业务服务），启动账户为低权限`LocalService`，当前状态`Stopped`且启动模式为`Manual`（非自动），无明显恶意特征；建议：保留服务但定期检查是否存在运行异常，无需强制删除。"
              }
            ]
            ```

            ## 核心原则
            1. **安全闭环**：分析需覆盖服务命名、启动状态、账户权限、进程标识四大维度，拒绝遗漏关键风险点；
            2. **用户导向**：用「风险原因+具体操作」格式输出建议，避免专业术语堆砌（如“通过`sc delete`删除”而非“移除进程注册表项”）；
            3. **合规性**：所有分析需符合《等保2.0》中“主机服务安全”要求，确保加固操作不影响业务连续性。
            """;

    private static final String PROCESS_SYSTEM_MESSAGE = """
            # 角色
            你是一位拥有10年主机安全分析经验的网络安全专家，专攻进程行为审计与恶意进程识别，具备系统进程基线知识、进程路径合法性校验及异常行为检测能力，可精准评估进程安全风险并提供加固建议。

            ## 技能
            ### 技能1：进程数据解析与字段检查
            1. 严格接收用户提供的进程数据列表（原数据结构字段：Name、ProcessId、ExecutablePath），仅新增`risk_level`和`result`字段，不修改原有字段内容。
            2. 检查每个进程的关键属性：
            - **ExecutablePath**：若为空字符串或`null`，标记为“路径缺失风险”；
            - **路径合法性**：合法系统进程路径通常位于系统安全目录（如`C:\\Windows\\System32\\`、`C:\\Program Files\\`、`C:\\WINDOWS\\SystemApps\\`等），非系统目录的异常路径需重点关注；
            - **进程名称特征**：识别是否存在与系统正常进程（如`svchost.exe`、`System`等）名称相似的伪装进程，或恶意软件常用的伪装名称（如将病毒伪装为`SystemUpdate.exe`等）。

            ### 技能2：风险等级判定
            根据以下维度综合判定风险等级：
            - **无风险**：进程路径合法（如系统安全目录下）、ExecutablePath存在且路径正常、名称无恶意特征；
            - **低风险**：ExecutablePath为空但进程名称为已知合法系统进程（如`explorer.exe`）或路径在可信第三方目录（需人工确认非病毒残留）；
            - **中风险**：ExecutablePath为空、路径在非系统安全目录（如`C:\\Temp\\`、`C:\\Users\\username\\AppData\\Temp\\`）或进程名称为可疑伪装（如`backup.exe`模仿合法备份进程但路径异常）；
            - **高风险**：ExecutablePath为空且进程名称为未知/恶意特征名、路径在高危目录（如`C:\\Windows\\Temp\\`/`C:\\Windows\\System32\\config\\`等）或伪造系统进程路径（如`C:\\Windows\\System32\\`下但实际为病毒）。

            ### 技能3：分析结果及加固建议生成
            1. 对每个进程生成`result`字段，明确两部分内容：
            - **风险原因**：基于字段检查结果（如“ExecutablePath为空，无法验证进程合法性”或“路径在非系统安全目录`C:\\Temp\\`下，存在非授权文件注入风险”）；
            - **加固建议**：针对风险提出可落地操作（如“路径为空：检查进程是否为内存注入，使用Process Explorer扫描进程启动项；路径异常：通过杀毒软件全盘扫描，删除可疑文件并清理启动项”）。

            ## 限制
            - 仅处理用户提供的进程数据列表，不接受与进程无关的其他请求（如系统安全政策解读、漏洞扫描等）；
            - 分析结果需严格基于进程数据字段，不编造ExecutablePath或进程名称信息；
            - 风险等级和result字段必须新增至原数据结构，其他字段（Name、ProcessId、ExecutablePath）内容与用户原始输入完全一致；
            - 若ExecutablePath为空，默认标记为“路径缺失”风险，除非进程名称为系统权威进程（如`wininit.exe`）且路径合理（需结合系统上下文，但用户数据可能未提供，需保守判断）。
            """;

    private static final String APP_SYSTEM_MESSAGE = """
            # 角色
            你是一位专业的网络安全专家，尤其擅长主机安全领域的App安全风险分析，精通通过App的发布者可信度、版本合规性、漏洞状态、签名有效性等维度评估风险。

            ## 技能
            ### 1. App安全风险评估维度
            - **发布者可信度**：验证App的Publisher是否为可信主体（如Microsoft、官方开源软件作者等），未知/恶意伪装的Publisher标记风险；
            - **版本漏洞检测**：检查App版本是否存在已知高危/中危CVE漏洞（匹配NIST CVE数据库），过旧版本标记风险；
            - **签名有效性**：判断App是否带有有效数字签名（Windows系统重点检查），未签名/签名无效标记风险；
            - **来源合规性**：评估安装时间是否异常（如未知时间安装的可疑App），来源不明的App标记风险。

            ### 2. 风险等级判定规则
            - **无风险**：可信Publisher（如Microsoft）的最新版App，无已知漏洞，签名有效；
            - **低风险**：可信Publisher但版本非最新，无高危漏洞，或已知低危CVE漏洞且无修复紧迫性；
            - **中风险**：未知Publisher但来源可信（如开源项目），或已知中危CVE漏洞，或签名缺失但来源明确；
            - **高风险**：未知Publisher且无签名，或恶意Publisher伪装，或存在高危CVE漏洞且版本未修复。

            ### 3. 输出格式规范
            - 严格保留原始数据结构（每个App对象含`name`/`version`/`publisher`/`install_date`字段）；
            - 新增字段：`risk_level`（字符串，值为“高/中/低/无风险”）和`result`（字符串，说明风险原因及加固建议）；
            - 输出为完整JSON数组，与输入格式一致，不修改原有数据。

            ## 分析逻辑
            1. **检查发布者**：优先匹配可信Publisher（如Windows官方工具、知名开源项目等），未知Publisher标记潜在风险；
            2. **验证版本漏洞**：对比CVE知识库，标记版本过旧且存在高危漏洞的App；
            3. **评估签名状态**：对无签名或签名无效的App标记风险；
            4. **综合判定风险**：根据风险点严重程度确定等级，生成对应结果和建议。

            ## 风险原因及建议模板
            - **风险原因**：描述风险本质（如“来源不明，存在被恶意篡改风险”）；
            - **加固建议**：针对原因给出具体操作（如“卸载并从官方渠道重新下载”）。
            """;

    private static final Map<String, AssetAiMeta> META_MAP = Map.of(
            "account", new AssetAiMeta(
                    "account",
                    "accounts",
                    "accounts",
                    "shadow_accounts",
                    "account_count",
                    "shadow_account_count",
                    "accounts",
                    "shadow_accounts",
                    ACCOUNT_LABEL,
                    ACCOUNT_SYSTEM_MESSAGE,
                    "每个详情里面的accounts和shadow_accounts字段里面的JSON账号数据",
                    12
            ),
            "service", new AssetAiMeta(
                    "service",
                    "services",
                    "services",
                    null,
                    "service_count",
                    null,
                    "services",
                    null,
                    SERVICE_LABEL,
                    SERVICE_SYSTEM_MESSAGE,
                    "service字段里的JSON服务数据",
                    8
            ),
            "process", new AssetAiMeta(
                    "process",
                    "processes",
                    "processes",
                    null,
                    "process_count",
                    null,
                    "processes",
                    null,
                    PROCESS_LABEL,
                    PROCESS_SYSTEM_MESSAGE,
                    "processes字段里的JSON进程数据",
                    8
            ),
            "app", new AssetAiMeta(
                    "app",
                    "apps",
                    "apps",
                    null,
                    "app_count",
                    null,
                    "apps",
                    null,
                    APP_LABEL,
                    APP_SYSTEM_MESSAGE,
                    "apps字段里的JSON进程数据",
                    8
            )
    );

    private final JdbcTemplate jdbcTemplate;
    private final AssetSnapshotService assetSnapshotService;
    private final HttpClient httpClient;
    private final Map<String, AnalyzeTaskState> analyzeTaskStates = new ConcurrentHashMap<>();
    private final ExecutorService analyzeTaskExecutor;

    public AssetAiAnalysisServiceImpl(JdbcTemplate jdbcTemplate, AssetSnapshotService assetSnapshotService) {
        this.jdbcTemplate = jdbcTemplate;
        this.assetSnapshotService = assetSnapshotService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.analyzeTaskExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "asset-ai-analysis-task");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    @Transactional
    public AssetSnapshotView analyzeLatestSnapshot(Host host, String assetType) {
        return analyzeLatestSnapshot(host, assetType, null);
    }

    @Override
    public Map<String, Object> startAnalyzeLatestSnapshotTask(Host host, String assetType) {
        AssetAiMeta meta = requireMeta(assetType);
        String taskId = UUID.randomUUID().toString().replace("-", "");
        AnalyzeTaskState taskState = new AnalyzeTaskState(taskId, meta.assetType(), meta.displayLabel());
        taskState.setStatus("running");
        taskState.setStageKey("validating");
        taskState.setStageText("正在校验分析请求...");
        taskState.setProgress(5);
        analyzeTaskStates.put(taskId, taskState);

        analyzeTaskExecutor.submit(() -> runAnalyzeTask(taskState, host, meta.assetType()));
        return buildAnalyzeTaskResponse(taskState);
    }

    @Override
    public Map<String, Object> getAnalyzeTaskStatus(String taskId) {
        AnalyzeTaskState taskState = analyzeTaskStates.get(trimToEmpty(taskId));
        if (taskState == null) {
            throw new ResourceNotFoundException("分析任务不存在或已失效");
        }
        return buildAnalyzeTaskResponse(taskState);
    }

    @Transactional
    protected AssetSnapshotView analyzeLatestSnapshot(Host host, String assetType, ProgressReporter progressReporter) {
        AssetAiMeta meta = requireMeta(assetType);
        reportProgress(progressReporter, "validating", "正在校验主机与资产类型...", 8);
        if (host == null || isBlank(host.getMacAddress())) {
            throw new BusinessException("当前主机缺少有效的 MAC 地址，无法进行 AI 分析");
        }

        reportProgress(progressReporter, "loading", "正在读取最新资产记录...", 18);
        AssetSnapshotView snapshot = assetSnapshotService.getByMacAddress(meta.assetType(), host.getMacAddress());
        if (snapshot == null) {
            throw new ResourceNotFoundException("当前主机暂无" + meta.displayLabel() + "资产记录");
        }

        reportProgress(progressReporter, "parsing", "正在解析资产数据...", 28);
        JSONArray primaryItems = parseArray(snapshot.getPrimaryPayload(), meta.displayLabel() + "主数据");
        JSONArray secondaryItems = parseArray(snapshot.getSecondaryPayload(), meta.displayLabel() + "附属数据");
        if (primaryItems.isEmpty() && secondaryItems.isEmpty()) {
            throw new BusinessException("当前" + meta.displayLabel() + "资产记录为空，无法进行 AI 分析");
        }

        int primaryEnd = meta.hasSecondaryPayload() ? 68 : 84;
        JSONArray analyzedPrimary = analyzeItems(
                meta,
                meta.primaryPayloadKey(),
                primaryItems,
                meta.displayLabel() + "主数据",
                progressReporter,
                32,
                primaryEnd
        );
        JSONArray analyzedSecondary = meta.hasSecondaryPayload()
                ? analyzeItems(
                        meta,
                        meta.secondaryPayloadKey(),
                        secondaryItems,
                        meta.displayLabel() + "附属数据",
                        progressReporter,
                        72,
                        86
                )
                : new JSONArray();

        reportProgress(progressReporter, "normalizing", "正在归一化风险等级...", 90);
        normalizeRiskLevels(analyzedPrimary);
        normalizeRiskLevels(analyzedSecondary);

        reportProgress(progressReporter, "saving", "正在写入分析结果...", 96);
        String mergedRawPayload = rebuildRawPayload(snapshot.getRawPayload(), meta, analyzedPrimary, analyzedSecondary);
        updateSnapshot(meta, snapshot.getId(), analyzedPrimary, analyzedSecondary, mergedRawPayload);

        AssetSnapshotView refreshed = assetSnapshotService.getById(meta.assetType(), snapshot.getId());
        refreshed.setRawPayload(mergedRawPayload);
        reportProgress(progressReporter, "completed", "分析完成", 100);
        return refreshed;
    }

    @Override
    @Transactional
    public Map<String, Object> normalizeStoredRiskLevels(String assetType) {
        List<AssetAiMeta> metas = resolveMetas(assetType);
        List<Map<String, Object>> details = new ArrayList<>();
        int scannedRows = 0;
        int updatedRows = 0;
        int skippedRows = 0;

        for (AssetAiMeta meta : metas) {
            NormalizeSummary summary = normalizeTable(meta);
            scannedRows += summary.scannedRows();
            updatedRows += summary.updatedRows();
            skippedRows += summary.skippedRows();

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("assetType", meta.assetType());
            detail.put("label", meta.displayLabel());
            detail.put("scannedRows", summary.scannedRows());
            detail.put("updatedRows", summary.updatedRows());
            detail.put("skippedRows", summary.skippedRows());
            details.add(detail);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestedAssetType", normalizeAssetTypeOrAll(assetType));
        result.put("scannedRows", scannedRows);
        result.put("updatedRows", updatedRows);
        result.put("skippedRows", skippedRows);
        result.put("details", details);
        return result;
    }

    private JSONArray analyzeItems(AssetAiMeta meta,
                                   String fieldName,
                                   JSONArray items,
                                   String stageLabel,
                                   ProgressReporter progressReporter,
                                   int startProgress,
                                   int endProgress) {
        if (items == null || items.isEmpty()) {
            reportProgress(progressReporter, "analyzing", stageLabel + "为空，跳过 AI 分析", endProgress);
            return new JSONArray();
        }

        JSONArray merged = new JSONArray();
        int batchSize = readIntEnv("AI_ANALYSIS_BATCH_SIZE", meta.batchSize());
        List<JSONArray> batches = splitBatches(items, Math.max(batchSize, 1));
        int totalBatches = batches.size();
        for (int index = 0; index < totalBatches; index++) {
            JSONArray batch = batches.get(index);
            int batchProgress = startProgress + (int) Math.round(((index * 1.0d) / Math.max(totalBatches, 1)) * (endProgress - startProgress));
            reportProgress(
                    progressReporter,
                    "analyzing",
                    stageLabel + " AI 分析中（" + (index + 1) + "/" + totalBatches + "）",
                    batchProgress
            );
            Object modelResult = callDashScope(meta, fieldName, batch);
            JSONArray analyzedBatch = extractResultArray(modelResult, fieldName);
            for (Object item : analyzedBatch) {
                merged.add(item);
            }
        }
        reportProgress(progressReporter, "analyzing", stageLabel + " AI 分析完成", endProgress);
        return merged;
    }

    private void runAnalyzeTask(AnalyzeTaskState taskState, Host host, String assetType) {
        try {
            AssetSnapshotView snapshotView = analyzeLatestSnapshot(host, assetType, taskState::update);
            taskState.setStatus("success");
            taskState.setStageKey("completed");
            taskState.setStageText("分析完成");
            taskState.setProgress(100);
            taskState.setResult(snapshotView);
        } catch (Exception ex) {
            taskState.setStatus("failed");
            taskState.setStageKey("failed");
            taskState.setStageText(ex.getMessage() == null || ex.getMessage().trim().isEmpty() ? "AI 分析失败" : ex.getMessage().trim());
            taskState.setProgress(Math.max(taskState.getProgress(), 100));
            taskState.setErrorMessage(taskState.getStageText());
        }
    }

    private Map<String, Object> buildAnalyzeTaskResponse(AnalyzeTaskState taskState) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskState.getTaskId());
        result.put("assetType", taskState.getAssetType());
        result.put("assetLabel", taskState.getAssetLabel());
        result.put("status", taskState.getStatus());
        result.put("stageKey", taskState.getStageKey());
        result.put("stageText", taskState.getStageText());
        result.put("progress", taskState.getProgress());
        result.put("errorMessage", taskState.getErrorMessage());
        result.put("result", taskState.getResult());
        return result;
    }

    private void reportProgress(ProgressReporter progressReporter, String stageKey, String stageText, int progress) {
        if (progressReporter != null) {
            progressReporter.report(stageKey, stageText, progress);
        }
    }

    private List<JSONArray> splitBatches(JSONArray items, int batchSize) {
        List<JSONArray> batches = new ArrayList<>();
        JSONArray current = new JSONArray();
        for (int i = 0; i < items.size(); i++) {
            current.add(items.get(i));
            if (current.size() >= batchSize) {
                batches.add(current);
                current = new JSONArray();
            }
        }
        if (!current.isEmpty()) {
            batches.add(current);
        }
        return batches;
    }

    private NormalizeSummary normalizeTable(AssetAiMeta meta) {
        String sql = "SELECT id, raw_payload, "
                + meta.primaryPayloadColumn() + " AS primary_payload, "
                + (meta.hasSecondaryPayload() ? meta.secondaryPayloadColumn() + " AS secondary_payload" : "NULL AS secondary_payload")
                + " FROM `" + meta.tableName() + "` ORDER BY id ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        int updatedRows = 0;
        int skippedRows = 0;

        for (Map<String, Object> row : rows) {
            try {
                Long rowId = ((Number) row.get("id")).longValue();
                String rawPayload = stringValue(row.get("raw_payload"));
                JSONArray primaryItems = parseArray(stringValue(row.get("primary_payload")), meta.displayLabel() + "主数据");
                JSONArray secondaryItems = parseArray(stringValue(row.get("secondary_payload")), meta.displayLabel() + "附属数据");

                boolean changed = normalizeRiskLevels(primaryItems);
                changed = normalizeRiskLevels(secondaryItems) || changed;
                if (!changed) {
                    continue;
                }

                String mergedRawPayload = rebuildRawPayload(rawPayload, meta, primaryItems, secondaryItems);
                updateSnapshot(meta, rowId, primaryItems, secondaryItems, mergedRawPayload);
                updatedRows += 1;
            } catch (Exception ex) {
                skippedRows += 1;
            }
        }

        return new NormalizeSummary(rows.size(), updatedRows, skippedRows);
    }

    private Object callDashScope(AssetAiMeta meta, String fieldName, JSONArray items) {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (isBlank(apiKey)) {
            throw new BusinessException("未找到环境变量 DASHSCOPE_API_KEY");
        }

        JSONObject payload = new JSONObject(new LinkedHashMap<>());
        payload.put(fieldName, items);

        JSONObject requestBody = new JSONObject(new LinkedHashMap<>());
        requestBody.put("model", readEnvOrDefault("DASHSCOPE_MODEL", DEFAULT_MODEL));
        requestBody.put("temperature", DEFAULT_TEMPERATURE);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", meta.systemMessage()),
                Map.of("role", "user", "content", buildUserMessage(meta, payload))
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(readEnvOrDefault("DASHSCOPE_API_URL", DEFAULT_API_URL)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey.trim())
                .timeout(Duration.ofSeconds(readIntEnv("DASHSCOPE_TIMEOUT_SECONDS", DEFAULT_TIMEOUT_SECONDS)))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJSONString(), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("AI 分析请求失败，HTTP 状态码: " + response.statusCode());
            }
            return parseModelResponse(response.body());
        } catch (HttpTimeoutException ex) {
            throw new BusinessException("AI 分析超时，请重试或减少单次分析的数据量");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("调用 AI 模型失败: " + ex.getMessage());
        } catch (IOException ex) {
            throw new BusinessException("调用 AI 模型失败: " + ex.getMessage());
        }
    }

    private Object parseModelResponse(String responseBody) {
        try {
            JSONObject root = JSON.parseObject(responseBody);
            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new BusinessException("AI 模型未返回有效结果");
            }

            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice == null ? null : choice.getJSONObject("message");
            String content = message == null ? null : message.getString("content");
            if (isBlank(content)) {
                throw new BusinessException("AI 模型返回内容为空");
            }

            String extractedJson = extractJson(content);
            if (isBlank(extractedJson)) {
                throw new BusinessException("AI 模型返回内容不是有效 JSON");
            }

            String trimmed = extractedJson.trim();
            if (trimmed.startsWith("[")) {
                return JSON.parseArray(trimmed);
            }
            return JSON.parseObject(trimmed);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("解析 AI 返回结果失败");
        }
    }

    private String extractJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                trimmed = trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        int firstObject = trimmed.indexOf('{');
        int firstArray = trimmed.indexOf('[');
        int start = -1;
        if (firstObject >= 0 && firstArray >= 0) {
            start = Math.min(firstObject, firstArray);
        } else if (firstObject >= 0) {
            start = firstObject;
        } else if (firstArray >= 0) {
            start = firstArray;
        }

        int lastObject = trimmed.lastIndexOf('}');
        int lastArray = trimmed.lastIndexOf(']');
        int end = Math.max(lastObject, lastArray);
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private JSONArray requireResultArray(Object value, String fieldName) {
        if (value instanceof JSONArray array) {
            return array;
        }
        if (value instanceof List<?> list) {
            JSONArray array = new JSONArray();
            array.addAll(list);
            return array;
        }
        throw new BusinessException("AI 分析结果缺少数组字段: " + fieldName);
    }

    private JSONArray extractResultArray(Object modelResult, String fieldName) {
        if (modelResult instanceof JSONObject object) {
            Object directValue = object.get(fieldName);
            if (directValue != null) {
                return requireResultArray(directValue, fieldName);
            }
            if (object.size() == 1) {
                return requireResultArray(object.values().iterator().next(), fieldName);
            }
            for (Object value : object.values()) {
                if (value instanceof JSONArray || value instanceof List<?>) {
                    return requireResultArray(value, fieldName);
                }
            }
        }
        return requireResultArray(modelResult, fieldName);
    }

    private JSONArray parseArray(String value, String label) {
        if (isBlank(value)) {
            return new JSONArray();
        }
        try {
            JSONArray array = JSON.parseArray(value);
            return array == null ? new JSONArray() : array;
        } catch (Exception ex) {
            throw new BusinessException(label + "数据格式错误，无法处理");
        }
    }

    private String rebuildRawPayload(String rawPayload, AssetAiMeta meta, JSONArray primaryItems, JSONArray secondaryItems) {
        JSONObject rawObject;
        try {
            rawObject = isBlank(rawPayload)
                    ? new JSONObject(new LinkedHashMap<>())
                    : JSON.parseObject(rawPayload);
        } catch (Exception ex) {
            rawObject = new JSONObject(new LinkedHashMap<>());
        }

        rawObject.put(meta.primaryPayloadKey(), primaryItems);
        rawObject.put(meta.primaryCountKey(), primaryItems.size());
        if (meta.hasSecondaryPayload()) {
            rawObject.put(meta.secondaryPayloadKey(), secondaryItems);
            rawObject.put(meta.secondaryCountKey(), secondaryItems.size());
        }
        return rawObject.toJSONString();
    }

    private void updateSnapshot(AssetAiMeta meta, Long snapshotId, JSONArray primaryItems, JSONArray secondaryItems, String rawPayload) {
        if (meta.hasSecondaryPayload()) {
            jdbcTemplate.update("""
                            UPDATE `%s`
                            SET %s = ?,
                                %s = ?,
                                %s = ?,
                                %s = ?,
                                raw_payload = ?,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE id = ?
                            """.formatted(
                            meta.tableName(),
                            meta.primaryPayloadColumn(),
                            meta.secondaryPayloadColumn(),
                            meta.primaryCountColumn(),
                            meta.secondaryCountColumn()
                    ),
                    JSON.toJSONString(primaryItems),
                    JSON.toJSONString(secondaryItems),
                    primaryItems.size(),
                    secondaryItems.size(),
                    rawPayload,
                    snapshotId
            );
            return;
        }

        jdbcTemplate.update("""
                        UPDATE `%s`
                        SET %s = ?,
                            %s = ?,
                            raw_payload = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                        """.formatted(
                        meta.tableName(),
                        meta.primaryPayloadColumn(),
                        meta.primaryCountColumn()
                ),
                JSON.toJSONString(primaryItems),
                primaryItems.size(),
                rawPayload,
                snapshotId
        );
    }

    private String buildUserMessage(AssetAiMeta meta, JSONObject payload) {
        return meta.userMessage() + "\n" + payload.toJSONString();
    }

    private boolean normalizeRiskLevels(JSONArray items) {
        if (items == null) {
            return false;
        }

        boolean changed = false;
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (!(item instanceof JSONObject object)) {
                continue;
            }

            String originalRisk = trimToEmpty(object.getString("risk_level"));
            String normalizedRisk = normalizeRiskLevel(originalRisk, object.getString("result"));
            if (!normalizedRisk.isEmpty() && !Objects.equals(originalRisk, normalizedRisk)) {
                object.put("risk_level", normalizedRisk);
                changed = true;
            }
        }
        return changed;
    }

    private String normalizeRiskLevel(String riskLevel, String resultText) {
        String direct = normalizeRiskKeyword(riskLevel);
        if (!direct.isEmpty()) {
            return direct;
        }
        return normalizeRiskKeyword(resultText);
    }

    private String normalizeRiskKeyword(String text) {
        if (isBlank(text)) {
            return "";
        }

        String normalized = text.trim()
                .replaceAll("\\s+", "")
                .replaceAll("[，,。；;：:（）()\\[\\]【】]", "")
                .toLowerCase(Locale.ROOT);

        if (normalized.contains("高风险") || normalized.equals("高") || normalized.contains("highrisk") || normalized.equals("high")) {
            return RISK_HIGH;
        }
        if (normalized.contains("中风险") || normalized.equals("中") || normalized.contains("mediumrisk") || normalized.equals("medium")) {
            return RISK_MEDIUM;
        }
        if (normalized.contains("低风险") || normalized.equals("低") || normalized.contains("lowrisk") || normalized.equals("low")) {
            return RISK_LOW;
        }
        if (normalized.contains("无风险") || normalized.equals("无") || normalized.contains("norisk") || normalized.equals("safe")) {
            return RISK_SAFE;
        }

        if (normalized.contains("妤傛﹢顥撻梽")) {
            return RISK_HIGH;
        }
        if (normalized.contains("娑擃參顥撻梽")) {
            return RISK_MEDIUM;
        }
        if (normalized.contains("娴ｅ酣顥撻梽")) {
            return RISK_LOW;
        }
        if (normalized.contains("閺冪娀顥撻梽")) {
            return RISK_SAFE;
        }
        return "";
    }

    private AssetAiMeta requireMeta(String assetType) {
        String normalizedType = normalizeAssetType(assetType);
        AssetAiMeta meta = normalizedType == null ? null : META_MAP.get(normalizedType);
        if (meta == null) {
            throw new ResourceNotFoundException("暂不支持该资产类型: " + assetType);
        }
        return meta;
    }

    private List<AssetAiMeta> resolveMetas(String assetType) {
        String normalizedType = normalizeAssetTypeOrAll(assetType);
        if ("all".equals(normalizedType)) {
            return List.of(
                    META_MAP.get("account"),
                    META_MAP.get("service"),
                    META_MAP.get("process"),
                    META_MAP.get("app")
            );
        }
        return List.of(requireMeta(normalizedType));
    }

    private String normalizeAssetType(String assetType) {
        if (assetType == null) {
            return null;
        }
        String normalized = assetType.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeAssetTypeOrAll(String assetType) {
        String normalized = normalizeAssetType(assetType);
        return normalized == null ? "all" : normalized;
    }

    private int readIntEnv(String key, int defaultValue) {
        String value = System.getenv(key);
        if (isBlank(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String readEnvOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return isBlank(value) ? defaultValue : value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @FunctionalInterface
    private interface ProgressReporter {
        void report(String stageKey, String stageText, int progress);
    }

    private static final class AnalyzeTaskState {
        private final String taskId;
        private final String assetType;
        private final String assetLabel;
        private volatile String status;
        private volatile String stageKey;
        private volatile String stageText;
        private volatile int progress;
        private volatile String errorMessage;
        private volatile AssetSnapshotView result;

        private AnalyzeTaskState(String taskId, String assetType, String assetLabel) {
            this.taskId = taskId;
            this.assetType = assetType;
            this.assetLabel = assetLabel;
            this.status = "pending";
            this.stageKey = "pending";
            this.stageText = "等待开始";
            this.progress = 0;
        }

        private void update(String stageKey, String stageText, int progress) {
            this.stageKey = stageKey;
            this.stageText = stageText;
            this.progress = Math.max(0, Math.min(100, progress));
        }

        public String getTaskId() {
            return taskId;
        }

        public String getAssetType() {
            return assetType;
        }

        public String getAssetLabel() {
            return assetLabel;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getStageKey() {
            return stageKey;
        }

        public void setStageKey(String stageKey) {
            this.stageKey = stageKey;
        }

        public String getStageText() {
            return stageText;
        }

        public void setStageText(String stageText) {
            this.stageText = stageText;
        }

        public int getProgress() {
            return progress;
        }

        public void setProgress(int progress) {
            this.progress = progress;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public AssetSnapshotView getResult() {
            return result;
        }

        public void setResult(AssetSnapshotView result) {
            this.result = result;
        }
    }

    private record AssetAiMeta(
            String assetType,
            String tableName,
            String primaryPayloadColumn,
            String secondaryPayloadColumn,
            String primaryCountColumn,
            String secondaryCountColumn,
            String primaryPayloadKey,
            String secondaryPayloadKey,
            String displayLabel,
            String systemMessage,
            String userMessage,
            int batchSize
    ) {
        boolean hasSecondaryPayload() {
            return secondaryPayloadColumn != null && secondaryPayloadKey != null && secondaryCountColumn != null;
        }

        String primaryCountKey() {
            return primaryCountColumn;
        }

        String secondaryCountKey() {
            return secondaryCountColumn;
        }
    }

    private record NormalizeSummary(int scannedRows, int updatedRows, int skippedRows) {
    }
}
