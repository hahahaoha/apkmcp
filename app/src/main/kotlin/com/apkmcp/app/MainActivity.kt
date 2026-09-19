package com.apkmcp.app

import android.Manifest
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apkmcp.app.capture.ScreenCaptureService
import com.apkmcp.app.core.Logs
import com.apkmcp.app.core.McpConfig
import com.apkmcp.app.ui.MainViewModel
import com.apkmcp.app.ui.UiStatus
import com.apkmcp.app.ui.theme.ApkMcpTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val cfg by vm.config.collectAsState()
            ApkMcpTheme(dynamicColor = cfg.dynamicColor) {
                AppRoot(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(vm: MainViewModel) {
    val ctx = LocalContext.current
    val cfg by vm.config.collectAsState()
    val status by vm.status.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val data = res.data
        if (res.resultCode == android.app.Activity.RESULT_OK && data != null) {
            ScreenCaptureService.start(ctx, res.resultCode, data)
            Logs.add("用户已授权屏幕捕获")
        } else {
            Logs.add("用户拒绝了屏幕捕获")
        }
        scope.launch {
            delay(700)
            vm.refresh()
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            vm.refresh()
            delay(1000)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("APK MCP", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (status.server) "运行中 · 端口 ${status.port}" else "未启动",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.PlayArrow, null) },
                    label = { Text("控制台") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.List, null) },
                    label = { Text("日志") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("设置") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                0 -> HomeTab(
                    vm = vm,
                    status = status,
                    onRequestCapture = {
                        val mpm = ctx.getSystemService(MediaProjectionManager::class.java)
                        projectionLauncher.launch(mpm.createScreenCaptureIntent())
                    },
                    onToast = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
                )
                1 -> LogsTab(vm)
                else -> SettingsTab(vm, cfg)
            }
        }
    }
}

@Composable
private fun HomeTab(
    vm: MainViewModel,
    status: UiStatus,
    onRequestCapture: () -> Unit,
    onToast: (String) -> Unit
) {
    val cfg by vm.config.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StatusCard(status) }

        item {
            SectionTitle("第 1 步 · 开启无障碍")
            ActionCard(
                title = "无障碍服务",
                desc = if (status.accessibility) "已开启，AI 可以点击和输入了"
                else "未开启。这是 AI 的「手」，必须开。在列表里找到「APK MCP 手机操作」并打开",
                done = status.accessibility,
                buttonText = if (status.accessibility) "去关闭" else "去开启",
                onClick = { vm.openAccessibilitySettings() }
            )
        }

        item {
            SectionTitle("第 2 步 · 允许后台启动应用")
            ActionCard(
                title = "悬浮窗权限",
                desc = if (status.overlay) "已开启。AI 可以自由跳转到别的 App"
                else "未开启。Android 10+ 会拦截后台启动 Activity，launch_app 会跳不过去",
                done = status.overlay,
                buttonText = if (status.overlay) "去管理" else "去开启",
                onClick = { vm.openOverlaySettings() }
            )
        }

        item {
            SectionTitle("第 3 步 · 开启屏幕捕获")
            ActionCard(
                title = "屏幕截图",
                desc = if (status.capture) "捕获中，AI 可以看到屏幕了"
                else "未开启。这是 AI 的「眼睛」。Android 14+ 每次重启 App 都要重新授权",
                done = status.capture,
                buttonText = if (status.capture) "停止捕获" else "开启捕获",
                onClick = { if (status.capture) vm.stopCapture() else onRequestCapture() }
            )
        }

        item {
            SectionTitle("第 4 步 · 启动 MCP 服务")
            ActionCard(
                title = "HTTP / MCP 接口",
                desc = if (status.server)
                    "监听 ${if (cfg.bindAll) "0.0.0.0" else "127.0.0.1"}:${status.port}\n" +
                        "POST http://127.0.0.1:${status.port}/mcp"
                else "未启动。启动后 AI 才能连进来",
                done = status.server,
                buttonText = if (status.server) "停止服务" else "启动服务",
                onClick = { vm.toggleServer() }
            )
        }

        item {
            SectionTitle("AI 客户端怎么连")
            ConnectCard(vm, status, onToast)
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatusCard(s: UiStatus) {
    val allReady = s.accessibility && s.capture && s.server
    val missing = listOf(s.accessibility, s.capture, s.server).count { !it }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (allReady) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (allReady) "已就绪" else "还差 $missing 项",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (allReady) "AI 现在可以看屏幕、点按钮了。"
                else "按下面顺序逐项打开即可。",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("无障碍", s.accessibility)
                Chip("截图", s.capture)
                Chip("服务", s.server)
                Chip("悬浮窗", s.overlay)
            }
            s.lastError?.let {
                Text(
                    "启动错误: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, ok: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (ok) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (ok) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ActionCard(
    title: String,
    desc: String,
    done: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                if (done) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onClick) {
                Icon(
                    if (done) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun ConnectCard(vm: MainViewModel, status: UiStatus, onToast: (String) -> Unit) {
    val cfg by vm.config.collectAsState()
    val url = "http://127.0.0.1:${cfg.port}/mcp"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("地址", style = MaterialTheme.typography.labelMedium)
            Text(url, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            if (cfg.token.isNotBlank()) {
                Text("请求头", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Authorization: Bearer ${cfg.token}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(
                "用 curl 测一下：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "curl -s $url -H 'content-type: application/json' -d '{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}'",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.restartServer(); onToast("已重启服务") }) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("重启服务")
                }
                OutlinedButton(onClick = { vm.openAccessibilitySettings() }) {
                    Text("无障碍设置")
                }
            }
        }
    }
}

@Composable
private fun LogsTab(vm: MainViewModel) {
    val lines by vm.logs.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${lines.size} 条",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { vm.clearLogs() }) { Text("清空") }
        }
        HorizontalDivider()
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "还没有日志",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(lines) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(vm: MainViewModel, cfg: McpConfig) {
    var port by remember(cfg) { mutableStateOf(cfg.port.toString()) }
    var token by remember(cfg) { mutableStateOf(cfg.token) }
    var bindAll by remember(cfg) { mutableStateOf(cfg.bindAll) }
    var maxWidth by remember(cfg) { mutableStateOf(cfg.maxWidth.toFloat()) }
    var quality by remember(cfg) { mutableStateOf(cfg.jpegQuality.toFloat()) }
    var dynamic by remember(cfg) { mutableStateOf(cfg.dynamicColor) }
    var sticky by remember(cfg) { mutableStateOf(cfg.sticky) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionTitle("服务")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("端口") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("访问令牌（留空 = 不校验）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { token = vm.randomToken() }) { Text("随机生成") }
                    OutlinedButton(onClick = { token = "" }) { Text("清空") }
                }
                SwitchRow(
                    title = "允许局域网访问",
                    desc = "开启后监听 0.0.0.0，同 Wi-Fi 的其他设备也能连。请务必设置令牌",
                    checked = bindAll,
                    onChange = { bindAll = it }
                )
                SwitchRow(
                    title = "被杀后自动重启服务",
                    desc = "保持接口常驻",
                    checked = sticky,
                    onChange = { sticky = it }
                )
            }
        }

        SectionTitle("截图")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("最长边 ${maxWidth.toInt()} px", style = MaterialTheme.typography.bodyMedium)
                Slider(value = maxWidth, onValueChange = { maxWidth = it }, valueRange = 320f..1440f)
                Text("JPEG 质量 ${quality.toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(value = quality, onValueChange = { quality = it }, valueRange = 20f..95f)
                Text(
                    "越小越省 token，但也越容易点不准。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionTitle("外观")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                SwitchRow(
                    title = "Material You 动态取色",
                    desc = "Android 12+ 跟随壁纸配色",
                    checked = dynamic,
                    onChange = { dynamic = it }
                )
            }
        }

        Button(
            onClick = {
                val p = port.toIntOrNull()?.coerceIn(1024, 65535) ?: 8765
                vm.save(
                    cfg.copy(
                        port = p,
                        token = token,
                        bindAll = bindAll,
                        maxWidth = maxWidth.toInt(),
                        jpegQuality = quality.toInt(),
                        dynamicColor = dynamic,
                        sticky = sticky
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存")
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SwitchRow(
    title: String,
    desc: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
