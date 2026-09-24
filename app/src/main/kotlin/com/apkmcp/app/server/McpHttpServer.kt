package com.apkmcp.app.server

import com.apkmcp.app.core.Logs
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayInputStream

/**
 * 进程内 HTTP 服务，同时提供：
 *
 *   POST /mcp                  MCP (JSON-RPC 2.0)
 *   GET  /health               健康检查
 *   GET  /tools                工具列表（REST 版）
 *   GET  /status               运行状态
 *   GET  /ui                   控件树
 *   GET  /apps                 App 列表
 *   GET  /screenshot.jpg       直接拿一张截图
 *   POST /tool/{name}          REST 方式直接调工具，body 是 JSON 参数
 */
class McpHttpServer(
    port: Int,
    private val token: String,
    bindAll: Boolean
) : NanoHTTPD(if (bindAll) "0.0.0.0" else "127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method

        if (method == Method.OPTIONS) {
            return withCors(newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, ""))
        }

        if (!authorized(session)) {
            return withCors(
                newFixedLengthResponse(
                    Response.Status.UNAUTHORIZED,
                    "application/json",
                    "{\"error\":\"unauthorized\"}"
                )
            )
        }

        return try {
            when {
                method == Method.GET && (uri == "/health" || uri == "/") -> json(healthJson())
                method == Method.GET && uri == "/tools" -> json(toolsJson())
                method == Method.GET && uri == "/status" -> json(restCall("get_status", null).toString())
                method == Method.GET && uri == "/ui" -> json(restCall("get_ui_tree", null).toString())
                method == Method.GET && uri == "/apps" -> json(restCall("list_apps", null).toString())
                method == Method.GET && uri.startsWith("/screenshot") -> screenshot()
                method == Method.POST && uri == "/mcp" -> mcp(session)
                method == Method.POST && uri.startsWith("/tool/") -> restTool(session, uri)
                else -> withCors(
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "application/json",
                        "{\"error\":\"not found: $uri\"}"
                    )
                )
            }
        } catch (t: Throwable) {
            Logs.add("HTTP 处理失败 $uri: ${t.message}")
            withCors(
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    "application/json",
                    "{\"error\":${quote(t.message ?: "unknown")}}"
                )
            )
        }
    }

    // ── 鉴权 ────────────────────────────────────────────────

    private fun authorized(session: IHTTPSession): Boolean {
        if (token.isBlank()) return true
        val auth = session.headers["authorization"] ?: session.headers["Authorization"]
        if (auth != null) {
            val v = auth.trim()
            if (v.equals("Bearer $token", true)) return true
            if (v.equals(token, true)) return true
        }
        val q = session.parameters["token"]?.firstOrNull()
        return q == token
    }

    // ── 基础响应 ────────────────────────────────────────────

    private fun json(body: String): Response =
        withCors(newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body))

    private fun withCors(r: Response): Response {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        r.addHeader("Cache-Control", "no-store")
        return r
    }

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private fun healthJson(): String = buildJsonObject {
        put("ok", true)
        put("name", "apkmcp")
        put("version", VERSION)
        put("protocol", PROTOCOL_VERSION)
    }.toString()

    private fun toolsJson(): String = buildJsonObject {
        putJsonArray("tools") {
            ToolRegistry.defs.forEach { add(it.toJson()) }
        }
    }.toString()

    // ── REST ────────────────────────────────────────────────

    private fun restCall(name: String, args: JsonObject?): JsonElement =
        ToolRegistry.call(name, args).toJson()

    private fun restTool(session: IHTTPSession, uri: String): Response {
        val name = uri.removePrefix("/tool/").substringBefore('?')
        val body = readBody(session)
        val args: JsonObject? = if (body.isBlank()) null else try {
            Json.parseToJsonElement(body).jsonObject
        } catch (t: Throwable) {
            null
        }
        Logs.add("REST 调用 $name")
        return json(restCall(name, args).toString())
    }

    private fun screenshot(): Response {
        val mgr = com.apkmcp.app.capture.ScreenCaptureService.instance
            ?: return json("{\"ok\":false,\"error\":\"capture not started\"}")
        val cfg = com.apkmcp.app.core.Prefs.config.value
        val cap = mgr.captureJpeg(cfg.maxWidth, cfg.jpegQuality)
            ?: return json("{\"ok\":false,\"error\":\"no frame yet\"}")
        val r = newFixedLengthResponse(
            Response.Status.OK,
            "image/jpeg",
            ByteArrayInputStream(cap.bytes),
            cap.bytes.size.toLong()
        )
        r.addHeader("X-Image-Width", cap.width.toString())
        r.addHeader("X-Image-Height", cap.height.toString())
        r.addHeader("X-Real-Width", mgr.realWidth.toString())
        r.addHeader("X-Real-Height", mgr.realHeight.toString())
        return withCors(r)
    }

    private fun readBody(session: IHTTPSession): String {
        return try {
            val map = HashMap<String, String>()
            session.parseBody(map)
            map["postData"] ?: ""
        } catch (t: Throwable) {
            ""
        }
    }

    // ── MCP JSON-RPC ────────────────────────────────────────

    private fun mcp(session: IHTTPSession): Response {
        val body = readBody(session)
        if (body.isBlank()) {
            return withCors(
                newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    "{\"error\":\"empty body\"}"
                )
            )
        }

        val root: JsonElement = try {
            Json.parseToJsonElement(body)
        } catch (t: Throwable) {
            return withCors(
                newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    rpcError(JsonNull, -32700, "Parse error")
                )
            )
        }

        if (root is JsonArray) {
            val parts = root.mapNotNull { el -> (el as? JsonObject)?.let { handleOne(it) } }
            if (parts.isEmpty()) {
                return withCors(
                    newFixedLengthResponse(Response.Status.ACCEPTED, "application/json", "")
                )
            }
            return json(parts.joinToString(",", "[", "]"))
        }

        val obj = root as? JsonObject
            ?: return withCors(
                newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    rpcError(JsonNull, -32600, "Invalid Request")
                )
            )

        val out = handleOne(obj)
            ?: return withCors(
                newFixedLengthResponse(Response.Status.ACCEPTED, "application/json", "")
            )
        return json(out)
    }

    /** 返回 null 表示这是通知（notification），不需要响应体 */
    private fun handleOne(req: JsonObject): String? {
        val id: JsonElement = req["id"] ?: JsonNull
        val isNotification = !req.containsKey("id")
        val method = req["method"]?.jsonPrimitive?.contentOrNull ?: ""
        val params: JsonObject? = req["params"] as? JsonObject

        if (isNotification) {
            Logs.add("MCP 通知 $method")
            return null
        }

        return try {
            when (method) {
                "initialize" -> rpcResult(id, initializeResult())
                "ping" -> rpcResult(id, buildJsonObject { })
                "tools/list" -> rpcResult(id, buildJsonObject {
                    putJsonArray("tools") {
                        ToolRegistry.defs.forEach { add(it.toJson()) }
                    }
                })
                "tools/call" -> rpcResult(id, callTool(params))
                "resources/list" -> rpcResult(id, buildJsonObject { putJsonArray("resources") { } })
                "prompts/list" -> rpcResult(id, buildJsonObject { putJsonArray("prompts") { } })
                else -> rpcError(id, -32601, "Method not found: $method")
            }
        } catch (t: Throwable) {
            rpcError(id, -32603, t.message ?: "internal error")
        }
    }

    private fun initializeResult(): JsonObject = buildJsonObject {
        put("protocolVersion", PROTOCOL_VERSION)
        putJsonObject("capabilities") {
            putJsonObject("tools") { put("listChanged", false) }
        }
        putJsonObject("serverInfo") {
            put("name", "apkmcp")
            put("version", VERSION)
        }
        put("instructions",
            "这是 Android 手机的屏幕与操作接口。先用 screenshot 或 get_ui_tree 看屏幕，" +
                "再用 find_and_tap / tap / swipe / type_text / press_key 操作。" +
                "tap 的坐标按 screenshot 返回的图片像素给。"
        )
    }

    private fun callTool(params: JsonObject?): JsonObject {
        val name = params?.get("name")?.jsonPrimitive?.contentOrNull
            ?: return buildJsonObject {
                put("isError", true)
                putJsonArray("content") {
                    addJsonObject {
                        put("type", "text")
                        put("text", "缺少工具名")
                    }
                }
            }
        val args = params["arguments"] as? JsonObject
        Logs.add("MCP 调用 $name")
        val t0 = System.currentTimeMillis()
        val result = ToolRegistry.call(name, args)
        val brief = result.text.lineSequence().firstOrNull()?.take(90).orEmpty()
        Logs.add("  └ ${if (result.isError) "失败" else "完成"} ${System.currentTimeMillis() - t0}ms  $brief")
        return result.toJson()
    }

    private fun rpcResult(id: JsonElement, result: JsonElement): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }.toString()

    private fun rpcError(id: JsonElement, code: Int, message: String): String = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }.toString()

    companion object {
        const val VERSION = "1.0.0"
        const val PROTOCOL_VERSION = "2024-11-05"
    }
}
