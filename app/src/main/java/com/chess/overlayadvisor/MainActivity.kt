package com.chess.overlayadvisor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var projectionManager: MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Overlay Aktif! Buka aplikasi catur sekarang.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Izin rekam layar ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Chess Screen Overlay Advisor"
            textSize = 22f
            setPadding(0, 0, 0, 24)
        }

        val desc = TextView(this).apply {
            text = "1. Berikan izin Tampil di Atas Aplikasi Lain.\n2. Tekan Mulai & izinkan tangkapan layar.\n3. Sesuaikan kotak kalibrasi di atas papan catur."
            textSize = 15f
            setPadding(0, 0, 0, 40)
        }

        val btnStart = Button(this).apply {
            text = "Mulai Overlay & Scan Layar"
            setOnClickListener { checkPermissionsAndStart() }
        }

        val btnStop = Button(this).apply {
            text = "Hentikan Overlay"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
            }
        }

        layout.addView(title)
        layout.addView(desc)
        layout.addView(btnStart)
        layout.addView(btnStop)
        setContentView(layout)
    }

    private fun checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Aktifkan izin Overlay lalu tekan Mulai lagi", Toast.LENGTH_LONG).show()
            return
        }
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
