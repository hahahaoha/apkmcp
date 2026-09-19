package com.apkmcp.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.apkmcp.app.capture.ScreenCaptureService
import com.apkmcp.app.control.AgentAccessibilityService
import com.apkmcp.app.core.Logs
import com.apkmcp.app.core.McpConfig
import com.apkmcp.app.core.Prefs
import com.apkmcp.app.server.McpServerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** 控制台的状态快照 */
data class UiStatus(
    val accessibility: Boolean = false,
    val capture: Boolean = false,
    val server: Boolean = false,
    val port: Int = 0,
    val lastError: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication()

    private val _status = MutableStateFlow(UiStatus())
    val status: StateFlow<UiStatus> = _status

    val logs = Logs.lines
    val config = Prefs.config

    fun refresh() {
        _status.value = UiStatus(
            accessibility = AgentAccessibilityService.ready(),
            capture = ScreenCaptureService.ready(),
            server = McpServerService.running(),
            port = if (McpServerService.running()) McpServerService.boundPort else 0,
            lastError = McpServerService.lastError
        )
    }

    fun openAccessibilitySettings() {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            Logs.add("打不开无障碍设置: ${t.message}")
        }
    }

    fun toggleServer() {
        if (McpServerService.running()) {
            McpServerService.stop(ctx)
        } else {
            McpServerService.start(ctx)
        }
        viewModelScope.launch {
            delay(500)
            refresh()
        }
    }

    fun restartServer() {
        McpServerService.restart(ctx)
        viewModelScope.launch {
            delay(900)
            refresh()
        }
    }

    fun stopCapture() {
        ScreenCaptureService.stop(ctx)
        viewModelScope.launch {
            delay(400)
            refresh()
        }
    }

    fun save(c: McpConfig) {
        val old = Prefs.config.value
        val needRestart = c.port != old.port || c.bindAll != old.bindAll || c.token != old.token
        Prefs.save(c)
        Logs.add("设置已保存")
        if (needRestart && McpServerService.running()) restartServer()
        refresh()
    }

    fun randomToken(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..24).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    fun clearLogs() = Logs.clear()
}
