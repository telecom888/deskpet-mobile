package com.shizuku.deskpet.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.WindowManager

class FloatingService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: FloatingView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showFloatingWindow()
            ACTION_HIDE -> hideFloatingWindow()
            ACTION_SEND_MESSAGE -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
                if (message.isNotBlank()) {
                    showFloatingWindow()
                    floatingView?.sendMessage(message)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFloatingWindow() {
        if (floatingView == null) {
            floatingView = FloatingView(this, windowManager!!)
            floatingView?.show()
        }
    }

    private fun hideFloatingWindow() {
        floatingView?.hide()
        floatingView = null
    }

    override fun onDestroy() {
        hideFloatingWindow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.shizuku.deskpet.ACTION_SHOW"
        const val ACTION_HIDE = "com.shizuku.deskpet.ACTION_HIDE"
        const val ACTION_SEND_MESSAGE = "com.shizuku.deskpet.ACTION_SEND_MESSAGE"
        const val EXTRA_MESSAGE = "message"
    }
}
