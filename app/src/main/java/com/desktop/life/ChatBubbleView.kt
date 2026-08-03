package com.desktop.life

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import java.util.*

/**
 * 原生AI聊天气泡界面
 * 纯View绘制，无WebView依赖
 */
class ChatBubbleView(context: Context) : FrameLayout(context) {

    // ==================== 回调 ====================
    var onSendMessage: ((String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null
    var onExpandChanged: ((Boolean) -> Unit)? = null
    var onVoiceInput: ((String) -> Unit)? = null

    // ==================== 状态 ====================
    private var isExpanded = false
    private var isThinking = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // ==================== UI组件 ====================
    private val chatList = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
    }

    private val scrollView = ScrollView(context).apply {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            0
        )
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(chatList)
    }

    private val inputField = EditText(context).apply {
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        )
        setLines(1)
        maxLines = 3
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setHintTextColor(Color.parseColor("#8899aa"))
        hint = "跟霍霍说点什么..."
        setTextColor(Color.WHITE)
        background = null
    }

    private val sendBtn = ImageButton(context).apply {
        layoutParams = LayoutParams(
            dp(36), dp(36)
        )
        setImageResource(android.R.drawable.ic_menu_send)
        setColorFilter(Color.parseColor("#a78bfa"))
        background = null
        isEnabled = false
        alpha = 0.4f
    }

    private val voiceBtn = ImageButton(context).apply {
        layoutParams = LayoutParams(
            dp(36), dp(36)
        )
        setImageResource(android.R.drawable.ic_btn_speak_now)
        setColorFilter(Color.parseColor("#22d3ee"))
        background = null
    }

    private val thinkingDots = TextView(context).apply {
        text = "● ● ●"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(Color.parseColor("#a78bfa"))
        gravity = Gravity.CENTER
        visibility = GONE
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, dp(40))
    }

    private val inputRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        addView(voiceBtn)
        addView(inputField, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(sendBtn)
    }

    private val titleBar = TextView(context).apply {
        text = "✦ 跟霍霍聊天"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(Color.parseColor("#8888aa"))
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(32))
    }

    private val aiSettingBtn = ImageButton(context).apply {
        layoutParams = LayoutParams(dp(28), dp(28))
        setImageResource(android.R.drawable.ic_menu_manage)
        setColorFilter(Color.parseColor("#6666aa"))
        background = null
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    init {
        // 设置背景
        setBackgroundDrawable(createRoundRectBg())
        setPadding(dp(2), dp(2), dp(2), dp(2))

        // 标题栏
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(32))
            addView(titleBar, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(aiSettingBtn)
        }

        // 输入区分割线
        val divider = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#22ffffff"))
        }

        // 组装布局
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            addView(titleRow)
            addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
            addView(thinkingDots)
            addView(divider)
            addView(inputRow)
        }
        addView(container)

        // ==================== 事件绑定 ====================

        sendBtn.setOnClickListener {
            val text = inputField.text.toString().trim()
            if (text.isNotEmpty()) {
                addMessage(text, isUser = true)
                onSendMessage?.invoke(text)
                inputField.text.clear()
                sendBtn.isEnabled = false
                sendBtn.alpha = 0.4f
            }
        }

        inputField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val hasText = !s.isNullOrBlank()
                sendBtn.isEnabled = hasText
                sendBtn.alpha = if (hasText) 1f else 0.4f
            }
        })

        // 语音输入按钮
        voiceBtn.setOnClickListener {
            toggleVoiceInput()
        }

        // 设置按钮
        aiSettingBtn.setOnClickListener {
            showSettingsDialog()
        }

        // 初始状态：折叠
        updateCollapsedState()
    }

    // ==================== 消息管理 ====================

    fun addMessage(text: String, isUser: Boolean) {
        val bubble = createBubble(text, isUser)
        chatList.addView(bubble)

        // 滚动到底部
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

        // 如果是第一条消息且有内容，自动展开
        if (chatList.childCount <= 1 && !isExpanded) {
            expand()
        }
    }

    fun addStreamText(text: String) {
        // 获取最后一条AI消息气泡，追加文本
        val lastIndex = chatList.childCount - 1
        if (lastIndex >= 0) {
            val lastView = chatList.getChildAt(lastIndex)
            if (lastView is TextView && lastView.tag == "ai") {
                lastView.append(text)
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                return
            }
        }
        // 没有AI气泡，创建新的
        val bubble = createBubble(text, false)
        bubble.tag = "ai"
        chatList.addView(bubble)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    fun setThinking(thinking: Boolean) {
        isThinking = thinking
        thinkingDots.visibility = if (thinking) VISIBLE else GONE
        inputField.isEnabled = !thinking
        sendBtn.isEnabled = !thinking && inputField.text.isNotBlank()
    }

    fun clearChat() {
        chatList.removeAllViews()
    }

    // ==================== 气泡渲染 ====================

    private fun createBubble(text: String, isUser: Boolean): View {
        val bgColor = if (isUser) Color.parseColor("#4a4a8a") else Color.parseColor("#2a2a4a")
        val tv = TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.WHITE)
            lineSpacing = 4f, 1.0f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundDrawable(createBubbleBg(bgColor, isUser))
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                if (isUser) {
                    gravity = Gravity.END
                }
                setMargins(
                    if (isUser) dp(48) else dp(8),
                    dp(4),
                    if (isUser) dp(8) else dp(48),
                    dp(4)
                )
            }
            maxWidth = dp(220)
            tag = if (isUser) "user" else "ai"
        }
        return tv
    }

    private fun createBubbleBg(color: Int, isUser: Boolean): android.graphics.drawable.Drawable {
        val radius = dp(12).toFloat()
        val path = Path()
        val rect = RectF(0f, 0f, dp(200).toFloat(), dp(60).toFloat())
        val corners = floatArrayOf(
            radius, radius, // top-left
            if (isUser) radius else 0f, if (isUser) radius else 0f, // top-right
            if (isUser) 0f else radius, if (isUser) 0f else radius, // bottom-right
            radius, radius  // bottom-left
        )
        return object : android.graphics.drawable.ShapeDrawable() {
            override fun draw(canvas: Canvas) {
                path.addRoundRect(rect, corners, Path.Direction.CW)
                paint.color = color
                paint.isAntiAlias = true
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun createRoundRectBg(): android.graphics.drawable.Drawable {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#cc1a1a2e")
            setShadowLayer(dp(8).toFloat(), 0f, dp(4).toFloat(), Color.parseColor("#40000000"))
        }
        return object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: Canvas) {
                val r = dp(16).toFloat()
                val rect = RectF(bounds)
                canvas.drawRoundRect(rect, r, r, paint)
                // 边框
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                paint.color = Color.parseColor("#33ffffff")
                canvas.drawRoundRect(rect, r, r, paint)
                paint.style = Paint.Style.FILL
            }
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(cf: ColorFilter?) {}
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    // ==================== 展开/折叠 ====================

    fun expand() {
        if (isExpanded) return
        isExpanded = true
        onExpandChanged?.invoke(true)

        val fullHeight = dp(320)
        val anim = ValueAnimator.ofInt(dp(48), fullHeight)
        anim.duration = 300
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.addUpdateListener {
            layoutParams?.height = it.animatedValue as Int
            requestLayout()
        }
        anim.start()
        updateCollapsedState()
    }

    fun collapse() {
        if (!isExpanded) return
        isExpanded = false
        onExpandChanged?.invoke(false)

        val anim = ValueAnimator.ofInt(layoutParams?.height ?: dp(320), dp(48))
        anim.duration = 200
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.addUpdateListener {
            layoutParams?.height = it.animatedValue as Int
            requestLayout()
        }
        anim.start()
        updateCollapsedState()
    }

    fun toggle() {
        if (isExpanded) collapse() else expand()
    }

    private fun updateCollapsedState() {
        visibility = VISIBLE
        alpha = 1f
    }

    // ==================== 语音输入 ====================

    private fun toggleVoiceInput() {
        if (isListening) {
            stopVoiceInput()
            return
        }
        startVoiceInput()
    }

    private fun startVoiceInput() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    voiceBtn.setColorFilter(Color.parseColor("#ef4444"))
                    inputField.hint = "正在听你说话..."
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        inputField.setText(text)
                        inputField.setSelection(text.length)
                        // 自动发送
                        addMessage(text, isUser = true)
                        onSendMessage?.invoke(text)
                        inputField.text.clear()
                    }
                    stopVoiceInput()
                }

                override fun onError(error: Int) {
                    inputField.hint = "跟霍霍说点什么..."
                    stopVoiceInput()
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "跟霍霍说话...")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            speechRecognizer?.startListening(intent)
        } else {
            Toast.makeText(context, "设备不支持语音识别", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoiceInput() {
        isListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        voiceBtn.setColorFilter(Color.parseColor("#22d3ee"))
        inputField.hint = "跟霍霍说点什么..."
    }

    // ==================== 设置对话框 ====================

    private fun showSettingsDialog() {
        val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog)
            .setTitle("AI 配置")
            .setView(createSettingsView())
            .setPositiveButton("保存") { d, _ ->
                saveSettings()
                d.dismiss()
            }
            .setNegativeButton("取消") { d, _ -> d.dismiss() }
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#1a1a2e")))
        dialog.show()
    }

    private lateinit var etApiUrl: EditText
    private lateinit var etApiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var ttsSwitch: Switch

    private fun createSettingsView(): View {
        val mgr = (context as? Service)?.let {
            AiChatManager::class.java.getDeclaredField("context").apply { isAccessible = true }
        }

        val padding = dp(16)

        etApiUrl = EditText(context).apply {
            setText("https://api.openai.com/v1/chat/completions")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#2a2a4a"))
            setPadding(padding, dp(8), padding, dp(8))
        }
        etApiKey = EditText(context).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#2a2a4a"))
            setPadding(padding, dp(8), padding, dp(8))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        etModel = EditText(context).apply {
            setText("gpt-3.5-turbo")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#2a2a4a"))
            setPadding(padding, dp(8), padding, dp(8))
        }
        ttsSwitch = Switch(context).apply {
            text = "语音播报"
            setTextColor(Color.WHITE)
            isChecked = true
        }

        // 从SharedPreferences加载已有配置
        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        etApiUrl.setText(prefs.getString("api_url", "https://api.openai.com/v1/chat/completions"))
        etApiKey.setText(prefs.getString("api_key", ""))
        etModel.setText(prefs.getString("model", "gpt-3.5-turbo"))
        ttsSwitch.isChecked = prefs.getBoolean("tts_enabled", true)

        val labelStyle = { text: String ->
            TextView(context).apply {
                this.text = text
                setTextColor(Color.parseColor("#a78bfa"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setPadding(0, dp(8), 0, dp(4))
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(labelStyle("API 地址"))
            addView(etApiUrl)
            addView(labelStyle("API Key"))
            addView(etApiKey)
            addView(labelStyle("模型"))
            addView(etModel)
            addView(ttsSwitch)
        }
    }

    private fun saveSettings() {
        val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("api_url", etApiUrl.text.toString().trim())
            putString("api_key", etApiKey.text.toString().trim())
            putString("model", etModel.text.toString().trim())
            putBoolean("tts_enabled", ttsSwitch.isChecked)
            apply()
        }
        Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
    }

    // ==================== 工具 ====================

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    fun isExpanded(): Boolean = isExpanded

    fun destroy() {
        stopVoiceInput()
    }
}