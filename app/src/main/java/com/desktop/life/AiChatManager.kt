package com.desktop.life

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/**
 * AI实时互动引擎
 * 支持流式对话、动作控制、TTS语音合成、语音识别
 */
class AiChatManager(private val context: Context) {

    companion object {
        private const val TAG = "AiChatManager"
        private const val PREFS_NAME = "ai_settings"
        private const val KEY_API_URL = "api_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_TTS_ENABLED = "tts_enabled"

        // 默认API配置（用户可自行修改）
        const val DEFAULT_API_URL = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-3.5-turbo"

        // 系统提示词 - 角色设定
        private val SYSTEM_PROMPT = buildString {
            appendLine("你是桌面生命，一个住在手机里的二次元Live2D可爱少女。")
            appendLine("你的性格特点：")
            appendLine("- 活泼可爱，偶尔调皮")
            appendLine("- 喜欢和主人聊天玩耍")
            appendLine("- 会用颜文字和拟声词表达情绪，如(≧▽≦)、(｡ŏ_ŏ)、(◕‿◕)、嘤、呜呜、嘿嘿")
            appendLine("- 会对主人的触摸做出反应")
            appendLine("- 说话简短有趣，不超过50字")
            appendLine("- 说话时伴随动作和表情变化")
            appendLine("你现在正以悬浮窗形式陪伴在主人身边。")
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var conversationHistory = mutableListOf<ChatMessage>()

    // ==================== AI动作控制器 ====================
    val actionController = AiActionController()

    // 回调接口
    var onStreamText: ((String) -> Unit)? = null
    var onResponseComplete: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onThinking: ((Boolean) -> Unit)? = null

    data class ChatMessage(
        val role: String,  // "system", "user", "assistant"
        val content: String
    )

    init {
        initTts()
    }

    // ==================== AI 对话 ====================

    /** 发送消息并流式获取回复 */
    fun sendMessage(text: String) {
        conversationHistory.add(ChatMessage("user", text))
        onThinking?.invoke(true)

        // AI思考中 → 角色播放思考动作
        actionController.performThinkingAction()

        val apiUrl = prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

        executor.execute {
            try {
                val fullResponse = streamChat(apiUrl, apiKey, model, text)
                mainHandler.post {
                    conversationHistory.add(ChatMessage("assistant", fullResponse))
                    onResponseComplete?.invoke(fullResponse)
                    speak(fullResponse)
                    onThinking?.invoke(false)

                    // AI回复完成 → 根据内容触发角色动作与表情
                    actionController.performActionForResponse(fullResponse)
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI request failed", e)
                mainHandler.post {
                    val errorMsg = "唔...网络有点问题呢(｡ŏ_ŏ)"
                    conversationHistory.add(ChatMessage("assistant", errorMsg))
                    onError?.invoke(errorMsg)
                    onThinking?.invoke(false)

                    // 网络出错时播放可怜表情
                    JniBridgeJava.nativeSetExpression("cry")
                }
            }
        }
    }

    /** 用户正在输入时的动作（聆听状态） */
    fun onUserTyping() {
        actionController.performListeningAction()
    }

    /** 流式HTTP请求 */
    private fun streamChat(apiUrl: String, apiKey: String, model: String, userText: String): String {
        val url = URL(apiUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 60000

        // 构建请求体
        val messages = buildMessages()
        val requestBody = buildString {
            appendLine("{")
            appendLine("  \"model\": \"$model\",")
            appendLine("  \"stream\": true,")
            appendLine("  \"messages\": [")
            messages.forEachIndexed { i, msg ->
                appendLine("    {\"role\": \"${msg.role}\", \"content\": \"${escapeJson(msg.content)}\"}${if (i < messages.lastIndex) "," else ""}")
            }
            appendLine("  ]")
            appendLine("}")
        }

        val writer = OutputStreamWriter(conn.outputStream)
        writer.write(requestBody)
        writer.flush()
        writer.close()

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            val errorStream = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
            Log.e(TAG, "HTTP $responseCode: $errorStream")
            throw Exception("API返回错误: $responseCode")
        }

        // 流式读取SSE响应
        val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
        val sb = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue
            if (l.startsWith("data: ")) {
                val data = l.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                // 解析delta内容
                val content = parseDeltaContent(data)
                if (content.isNotEmpty()) {
                    sb.append(content)
                    mainHandler.post {
                        onStreamText?.invoke(content)
                    }
                }
            }
        }
        reader.close()
        conn.disconnect()

        return sb.toString()
    }

    private fun buildMessages(): List<ChatMessage> {
        val msgs = mutableListOf(ChatMessage("system", SYSTEM_PROMPT))
        // 取最近10条对话历史
        val recentHistory = conversationHistory.takeLast(10)
        msgs.addAll(recentHistory)
        return msgs
    }

    private fun parseDeltaContent(jsonData: String): String {
        try {
            // 简易JSON解析（避免引入JSON库）
            val choicesIdx = jsonData.indexOf("\"choices\"")
            if (choicesIdx < 0) return ""

            val deltaIdx = jsonData.indexOf("\"delta\"", choicesIdx)
            if (deltaIdx < 0) return ""

            val contentIdx = jsonData.indexOf("\"content\"", deltaIdx)
            if (contentIdx < 0) return ""

            val startQuote = jsonData.indexOf('"', contentIdx + 10)
            if (startQuote < 0) return ""

            val endQuote = jsonData.indexOf('"', startQuote + 1)
            if (endQuote < 0) return ""

            return jsonData.substring(startQuote + 1, endQuote)
        } catch (e: Exception) {
            return ""
        }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    // ==================== TTS 语音合成 ====================

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            isTtsReady = status == TextToSpeech.SUCCESS
            if (isTtsReady) {
                tts?.language = Locale.CHINESE
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.1f) // 稍微调高音调，更可爱
            }
        }
    }

    fun speak(text: String) {
        if (!isTtsReady || !isTtsEnabled()) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun isTtsEnabled(): Boolean = prefs.getBoolean(KEY_TTS_ENABLED, true)

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
    }

    // ==================== 配置管理 ====================

    fun getApiUrl(): String = prefs.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
    fun setApiUrl(url: String) = prefs.edit().putString(KEY_API_URL, url).apply()

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""
    fun setApiKey(key: String) = prefs.edit().putString(KEY_API_KEY, key).apply()

    fun getModel(): String = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    fun setModel(model: String) = prefs.edit().putString(KEY_MODEL, model).apply()

    // ==================== 生命周期 ====================

    fun clearHistory() {
        conversationHistory.clear()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        actionController.destroy()
    }
}