package com.desktop.life

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val OVERLAY_PERMISSION_REQ = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.status_text)
        val startBtn = findViewById<Button>(R.id.btn_start)
        val stopBtn = findViewById<Button>(R.id.btn_stop)

        updateStatus(statusText)

        startBtn.setOnClickListener {
            if (checkOverlayPermission()) {
                startLife()
            } else {
                requestOverlayPermission()
            }
        }

        stopBtn.setOnClickListener {
            stopService(Intent(this, FloatingLifeService::class.java))
            updateStatus(statusText)
            Toast.makeText(this, "桌面生命已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQ)
        Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
    }

    private fun startLife() {
        startService(Intent(this, FloatingLifeService::class.java))
        moveTaskToBack(true)
        Toast.makeText(this, "桌面生命已启动！", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateStatus(tv: TextView) {
        tv.text = if (FloatingLifeService.isRunning) {
            "✦ 桌面生命正在陪伴你"
        } else {
            "✦ 桌面生命未启动\n\n点击下方按钮唤醒"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ) {
            if (checkOverlayPermission()) {
                startLife()
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能使用", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.status_text)?.let { updateStatus(it) }
    }
}