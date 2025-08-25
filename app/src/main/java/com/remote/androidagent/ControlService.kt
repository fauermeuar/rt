package com.remote.androidagent // <- Pastikan baris ini juga sesuai



import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.Scanner

class ControlService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val SERVER_PORT = 8080
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "ControlServiceChannel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultData != null) {
                    // Mulai server di background thread
                    serviceScope.launch {
                        startServer(resultData)
                    }
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private suspend fun startServer(resultData: Intent) {
        // TODO: Inisialisasi MediaProjection di sini dengan `resultData` untuk mulai menangkap layar.
        // Logika streaming video akan ditempatkan di sini.

        val serverSocket = ServerSocket(SERVER_PORT)
        while (isActive) {
            try {
                val clientSocket = serverSocket.accept()
                // Handle setiap koneksi di coroutine baru
                launch { handleClient(clientSocket) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                val scanner = Scanner(socket.getInputStream())
                val output: OutputStream = socket.getOutputStream()

                if (scanner.hasNextLine()) {
                    val requestLine = scanner.nextLine()
                    // Contoh parsing request HTTP GET sederhana
                    val parts = requestLine.split(" ")
                    if (parts.isNotEmpty() && parts[0] == "GET") {
                        val path = parts[1]
                        handleRequest(path) // Proses perintah
                    }
                }

                // Kirim response HTTP sederhana
                val response = "HTTP/1.1 200 OK\r\n\r\nCommand Received"
                output.write(response.toByteArray())
                output.flush()

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket.close()
            }
        }
    }

    private fun handleRequest(path: String) {
        // Contoh: http://<ip>:8080/touch?x=100&y=200
        if (path.startsWith("/touch")) {
            val params = path.substringAfter("?").split("&")
            val x = params.find { it.startsWith("x=") }?.substringAfter("=")
            val y = params.find { it.startsWith("y=") }?.substringAfter("=")

            if (x != null && y != null) {
                executeRootCommand("input tap $x $y")
            }
        }
        // Tambahkan perintah lain di sini (misal: /swipe, /key, /start_stream)
    }

    private fun executeRootCommand(command: String) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.waitFor()
        } catch (e: Exception) {
            // Gagal eksekusi root
            e.printStackTrace()
        }
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Remote Control Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Layanan Kontrol Aktif")
            .setContentText("Aplikasi sedang berjalan di latar belakang.")
            .setSmallIcon(R.mipmap.ic_launcher) // Ganti dengan ikon notifikasi Anda
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel() // Hentikan semua coroutine saat service dimatikan
    }
}