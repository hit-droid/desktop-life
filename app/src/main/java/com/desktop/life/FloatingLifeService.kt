package com.desktop.life

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 桌面生命 - 纯原生Live2D悬浮窗服务
 * 使用GLSurfaceView + Cubism SDK进行OpenGL ES2渲染
 */
class FloatingLifeService : Service() {

    private var windowManager: WindowManager? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var containerView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var isDragging = false

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
    }

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

    private fun setupFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 创建GLSurfaceView作为Live2D渲染容器
        glSurfaceView = object : GLSurfaceView(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                handleTouch(event)
                return true
            }
        }

        // 设置EGL配置 - 透明背景
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

        // 透明背景
        glSurfaceView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        glSurfaceView?.setZOrderOnTop(true)
        glSurfaceView?.holder?.setFormat(PixelFormat.TRANSLUCENT)

        containerView = glSurfaceView

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        // 浮窗尺寸约为屏幕宽度的35%
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

    private fun handleTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params!!.x
                initialY = params!!.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false

                // 传递触摸坐标给Live2D
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
                } else {
                    val localX = event.x / (params!!.width)
                    val localY = 1.0f - event.y / (params!!.height)
                    JniBridgeJava.nativeOnTouchesMoved(localX, localY)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // 点击/触摸事件
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 300) {
                        // 双击
                        JniBridgeJava.nativeOnTouchesEnded(
                            event.x / (params!!.width),
                            1.0f - event.y / (params!!.height)
                        )
                    } else {
                        // 单击 - 传递给Live2D作为触摸结束
                        JniBridgeJava.nativeOnTouchesEnded(
                            event.x / (params!!.width),
                            1.0f - event.y / (params!!.height)
                        )
                    }
                    lastTapTime = now
                } else {
                    // 拖拽结束，吸附到边缘
                    snapToEdge()
                }
            }
        }
    }

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
                if (progress < 1) containerView?.postDelayed(this, 16)
            }
        }
        containerView?.post(anim)
    }

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
        JniBridgeJava.nativeOnStop()
        JniBridgeJava.nativeOnDestroy()
        glSurfaceView?.onPause()
        glSurfaceView?.let { windowManager?.removeView(it) }
    }
}