package com.desktop.life

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 桌面生命 - 纯原生Live2D悬浮窗服务
 * 集成AI实时互动引擎
 */
class FloatingLifeService : Service() {

    private var windowManager: WindowManager? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var containerView: View? = null
    private var params: WindowManager.LayoutParams? = null

    // ==================== AI组件 ====================
    private lateinit var aiManager: AiChatManager
    private var chatBubbleView: ChatBubbleView? = null
    private var chatParams: WindowManager.LayoutParams? = null

    // ==================== 触摸状态 ====================
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var tapCount = 0
    private var isDragging = false
    private var isChatExpanded = false

    companion object {
        private const val CHANNEL_ID = "desktop_life_channel"
        private const val NOTIFICATION_ID = 1
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        JniBridgeJava.SetContext(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        setupFloatingWindow()
        setupAiChat()
    }

    // ==================== 通知 ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "桌面生命",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "桌面生命正在运行"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, FloatingLifeService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("桌面生命")
            .setContentText("正在陪伴你...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ==================== 悬浮窗设置 ====================

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        glSurfaceView = object : GLSurfaceView(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                handleTouch(event)
                return true
            }
        }

        glSurfaceView?.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        glSurfaceView?.setEGLContextClientVersion(2)
        glSurfaceView?.setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                JniBridgeJava.nativeOnStart()
                JniBridgeJava.nativeOnSurfaceCreated()
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                JniBridgeJava.nativeOnSurfaceChanged(width, height)
            }

            override fun onDrawFrame(gl: GL10?) {
                JniBridgeJava.nativeOnDrawFrame()
            }
        })
        glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        glSurfaceView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        glSurfaceView?.setZOrderOnTop(true)
        glSurfaceView?.holder?.setFormat(PixelFormat.TRANSLUCENT)

        containerView = glSurfaceView

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val windowSize = (screenWidth * 0.35).toInt()

        params = WindowManager.LayoutParams(
            windowSize,
            windowSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - windowSize - 20
            y = screenHeight / 3
        }

        windowManager?.addView(containerView, params)
    }

    // ==================== AI 聊天 ====================

    private fun setupAiChat() {
        aiManager = AiChatManager(this)

        // 创建聊天气泡View
        chatBubbleView = ChatBubbleView(this).apply {
            layoutParams = WindowManager.LayoutParams(
                dp(280),
                dp(48),  // 初始折叠高度
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )

            // 消息发送回调
            onSendMessage = { text ->
                aiManager.sendMessage(text)
            }

            // 展开/折叠回调
            onExpandChanged = { expanded ->
                isChatExpanded = expanded
                updateChatWindowFlags(expanded)
            }
        }

        // AI回调 -> Chat UI
        aiManager.onStreamText = { text ->
            chatBubbleView?.addStreamText(text)
        }

        aiManager.onResponseComplete = { text ->
            chatBubbleView?.setThinking(false)
        }

        aiManager.onError = { msg ->
            chatBubbleView?.addMessage(msg, false)
            chatBubbleView?.setThinking(false)
        }

        aiManager.onThinking = { thinking ->
            chatBubbleView?.setThinking(thinking)
        }

        // 在当前Live2D浮窗下方放置聊天窗
        chatParams = WindowManager.LayoutParams(
            dp(280),
            dp(48),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = params?.x ?: 0
            y = (params?.y ?: 0) + (params?.height ?: 0) + dp(8)
        }

        windowManager?.addView(chatBubbleView, chatParams)
    }

    private fun updateChatWindowFlags(expanded: Boolean) {
        chatParams?.flags = if (expanded) {
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        }
        chatBubbleView?.let { windowManager?.updateViewLayout(it, chatParams) }
    }

    // ==================== 触摸处理 ====================

    private fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params!!.x
                initialY = params!!.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false

                val localX = event.x / (params!!.width)
                val localY = 1.0f - event.y / (params!!.height)
                JniBridgeJava.nativeOnTouchesBegan(localX, localY)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                    isDragging = true
                    params?.x = initialX + dx.toInt()
                    params?.y = initialY + dy.toInt()
                    windowManager?.updateViewLayout(containerView, params)
                    // 同步更新聊天窗位置
                    syncChatPosition()
                } else {
                    val localX = event.x / (params!!.width)
                    val localY = 1.0f - event.y / (params!!.height)
                    JniBridgeJava.nativeOnTouchesMoved(localX, localY)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    handleTap(event)
                } else {
                    snapToEdge()
                    syncChatPosition()
                }
            }
        }
    }

    private fun handleTap(event: MotionEvent) {
        val now = System.currentTimeMillis()

        if (now - lastTapTime < 300) {
            tapCount++
        } else {
            tapCount = 1
        }
        lastTapTime = now

        when (tapCount) {
            1 -> {
                // 单击：触摸互动
                JniBridgeJava.nativeOnTouchesEnded(
                    event.x / (params!!.width),
                    1.0f - event.y / (params!!.height)
                )
            }
            2 -> {
                // 双击：打开/关闭聊天
                tapCount = 0
                toggleChat()
            }
            3 -> {
                // 三击：打招呼
                tapCount = 0
                sayHello()
            }
        }
    }

    private fun toggleChat() {
        if (isChatExpanded) {
            chatBubbleView?.collapse()
        } else {
            chatBubbleView?.expand()
        }
    }

    private fun sayHello() {
        val hellos = listOf(
            "主人来啦！(≧▽≦)",
            "嘿嘿，想我了吗？",
            "今天过得怎么样呀~",
            "陪我聊聊天嘛~"
        )
        val greeting = hellos.random()
        chatBubbleView?.addMessage(greeting, false)
        aiManager.speak(greeting)
    }

    private fun syncChatPosition() {
        chatParams?.let { p ->
            p.x = params?.x ?: 0
            p.y = (params?.y ?: 0) + (params?.height ?: 0) + dp(8)
            chatBubbleView?.let { windowManager?.updateViewLayout(it, p) }
        }
    }

    // ==================== 边缘吸附 ====================

    private fun snapToEdge() {
        val p = params ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val targetX = if (p.x + p.width / 2 < screenWidth / 2) 0
        else screenWidth - p.width

        val startX = p.x
        val dist = targetX - startX
        val duration = 200L
        val startTime = System.currentTimeMillis()

        val anim = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                val eased = 1 - (1 - progress) * (1 - progress)
                p.x = startX + (dist * eased).toInt()
                windowManager?.updateViewLayout(containerView, p)
                syncChatPosition()
                if (progress < 1) containerView?.postDelayed(this, 16)
            }
        }
        containerView?.post(anim)
    }

    // ==================== 生命周期 ====================

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false

        // 清理AI资源
        aiManager.destroy()
        chatBubbleView?.destroy()
        chatBubbleView?.let { windowManager?.removeView(it) }

        // 清理渲染
        JniBridgeJava.nativeOnStop()
        JniBridgeJava.nativeOnDestroy()
        glSurfaceView?.onPause()
        glSurfaceView?.let { windowManager?.removeView(it) }
    }

    private fun dp(value: Int): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }
}