package com.remote.androidagent // <- Pastikan baris ini sesuai

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                // Kirim izin ke Service
                val serviceIntent = Intent(this, ControlService::class.java).apply {
                    action = ControlService.ACTION_START
                    putExtra(ControlService.EXTRA_RESULT_DATA, data)
                }
                startForegroundService(serviceIntent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvStatusRoot: TextView = findViewById(R.id.tv_status_root)
        val tvIpAddress: TextView = findViewById(R.id.tv_ip_address)
        val btnStartService: Button = findViewById(R.id.btn_start_service)

        // 1. Cek status root
        tvStatusRoot.text = if (isRooted()) "Status Root: Tersedia" else "Status Root: Tidak Tersedia (Sentuhan Cepat Nonaktif)"

        // 2. Tampilkan Alamat IP dan Port
        tvIpAddress.text = "Alamat Akses: http://${getIpAddress()}:${ControlService.SERVER_PORT}"

        // 3. Setup Tombol untuk meminta izin
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        btnStartService.setOnClickListener {
            projectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }

        // Coba mulai service secara otomatis jika memungkinkan
        startServiceWithPermission()
    }

    private fun startServiceWithPermission() {
        // Secara default, kita butuh interaksi user pertama kali
        // Anda bisa menyimpan izin `data` di SharedPreferences untuk start otomatis lain kali,
        // namun itu lebih kompleks dan tidak selalu bekerja di Android versi baru.
        // Cara paling andal adalah meminta izin via tombol setiap kali aplikasi dibuka.
        findViewById<Button>(R.id.btn_start_service).performClick()
    }

    private fun getIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format(Locale.getDefault(), "%d.%d.%d.%d",
            ipAddress and 0xff, ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff)
    }

    private fun isRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su"
        )
        for (path in paths) {
            if (java.io.File(path).exists()) return true
        }
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.readLine() != null
        } catch (e: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }
}