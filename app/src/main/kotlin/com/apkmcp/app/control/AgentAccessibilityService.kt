package com.apkmcp.app.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.apkmcp.app.core.Logs

/**
 * 唯一的「手」：点击 / 长按 / 滑动 / 输入 / 返回。
 * 全部走无障碍 API，不需要 root，不需要 adb。
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Logs.add("无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        Logs.add("无障碍服务已断开")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    // ── 基本信息 ────────────────────────────────────────────

    fun rootNode(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (t: Throwable) {
        null
    }

    fun currentPackage(): String? = try {
        rootInActiveWindow?.packageName?.toString()
    } catch (t: Throwable) {
        null
    }

    fun realSize(): Pair<Int, Int> {
        val dm = resources.displayMetrics
        return dm.widthPixels to dm.heightPixels
    }

    fun uiTree(maxNodes: Int = 300): String = UiTreeReader.dump(rootNode(), maxNodes)

    // ── 手势 ────────────────────────────────────────────────

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, 0L, 60L)
    }

    fun longPress(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, 0L, 650L)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        return dispatch(path, 0L, durationMs.coerceIn(50L, 5000L))
    }

    private fun dispatch(path: Path, start: Long, duration: Long): Boolean {
        return try {
            val stroke = GestureDescription.StrokeDescription(path, start, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, null, null)
        } catch (t: Throwable) {
            Logs.add("手势失败: ${t.message}")
            false
        }
    }

    // ── 控件查找 ────────────────────────────────────────────

    fun findNode(needle: String, exact: Boolean = false): AccessibilityNodeInfo? {
        val root = rootNode() ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 4000) {
            val n = queue.removeFirst()
            visited++
            if (matches(n.text?.toString(), needle, exact) ||
                matches(n.contentDescription?.toString(), needle, exact) ||
                matches(n.hintText?.toString(), needle, exact)
            ) {
                return n
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun matches(value: String?, needle: String, exact: Boolean): Boolean {
        if (value.isNullOrBlank()) return false
        return if (exact) value.equals(needle, ignoreCase = true)
        else value.contains(needle, ignoreCase = true)
    }

    fun tapNode(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return false
        val cx = rect.exactCenterX()
        val cy = rect.exactCenterY()
        return tap(cx, cy)
    }

    fun scrollNode(node: AccessibilityNodeInfo, forward: Boolean): Boolean {
        return try {
            node.performAction(
                if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            )
        } catch (t: Throwable) {
            false
        }
    }

    // ── 输入 ────────────────────────────────────────────────

    fun focusedEditable(): AccessibilityNodeInfo? {
        val root = rootNode() ?: return null
        root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { return it }
        return firstEditable(root, 0)
    }

    private fun firstEditable(n: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 30) return null
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            firstEditable(c, depth + 1)?.let { return it }
        }
        return null
    }

    fun typeText(text: String, replace: Boolean = true): Boolean {
        val target = focusedEditable() ?: return false
        return try {
            val args = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
            }
            target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (t: Throwable) {
            Logs.add("输入失败: ${t.message}")
            false
        }
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        fun ready(): Boolean = instance != null
    }
}
