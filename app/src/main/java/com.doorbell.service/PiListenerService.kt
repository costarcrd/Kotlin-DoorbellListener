package com.doorbell.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.net.ServerSocket
import java.util.Properties

class PiListenerService : Service() {

    private lateinit var config: Properties
    private var requestCount = 0
    private var serverThread: Thread? = null
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var running = false
    private val CHANNEL_ID = "doorbell_channel"

    override fun onCreate() {
        super.onCreate()
        Log.i("PiListenerService", "Service create")
        config = loadOrCreateConfig()
        createNotificationChannel()
        requestCount = 0

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Doorbell Service")
            .setContentText("aguardar conexões…")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(1, notification)

        Log.i("PiListenerService", "Service iniciado com notification")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //startForeground(1, buildNotification(requestCount.toString()))
        if (running) return START_STICKY
        running = true
        serverThread = Thread {
            val port = config.getProperty("port", "9000").toInt()
            val authorizedIp = config.getProperty("authorized_ip", "0.0.0.0")
            try {
                serverSocket = ServerSocket(port)
                Log.i("PiListenerService", "Servidor na porta $port")
                while (running) {
                    val sock = serverSocket!!.accept()
                    val clientIp = sock.inetAddress.hostAddress
                    Log.i("PiListenerService", "Conexão de $clientIp")
                    if (authorizedIp != "0.0.0.0" && clientIp != authorizedIp) {
                        Log.w("PiListenerService", "IP não autorizado: $clientIp")
                        sock.close()
                        continue
                    }
                    val line = sock.getInputStream().bufferedReader().readLine()
                    Log.i("PiListenerService", "Recebido: $line")
                    if (line == "RING") trigger()
                    requestCount++
                    updateNotification(requestCount)
                    sock.close()
                }
            } catch (e: Exception) {
                Log.e("PiListenerService", "Erro no socket: ${e.message}")
            }
        }
        serverThread!!.start()
        return START_STICKY
    }

    private fun trigger() {
        try {
            val url = config.getProperty("url", "file:///storage/emulated/0/DoorbellService/page.html")
            val mp3 = config.getProperty("mp3", "/storage/emulated/0/DoorbellService/sound.mp3")
            Log.i("PiListenerService", "Trigger → url=$url mp3=$mp3")
            val i = Intent(this, DisplayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("url", url)
                putExtra("mp3", mp3)
            }
            startActivity(i)
        } catch (e: Exception) {
            Log.e("PiListenerService", "Trigger erro: ${e.message}")
        }

    }

    private fun updateNotification(count: Int) {
        //val nm = getSystemService(NotificationManager::class.java)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Doorbell Service")
            .setContentText("Pedidos recebidos: $count")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        nm.notify(1, notification)
    }

    private fun buildNotification(status: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Doorbell Service")
            .setContentText("Pedidos recebidos: $status")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Doorbell Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        Log.i("PiListenerService", "Service destruído")
        super.onDestroy()
    }

    private fun loadOrCreateConfig(): Properties {
        //val dir = File("/storage/emulated/0/DoorbellService")
        val dir = File(Environment.getExternalStorageDirectory(), "DoorbellService")
        if (!dir.exists()) {
            val created = dir.mkdirs()
            Log.i("PiListenerService", "Pasta criada: $created -> ${dir.absolutePath}")
        } else {
            Log.i("PiListenerService", "Pasta já existe: ${dir.absolutePath}")
        }
        val cfgFile = File(dir, "config.ini")
        if (!cfgFile.exists()) {
            val initialContent = """
            port=9000
            url=https://www.sapo.pt
            mp3=/storage/emulated/0/DoorbellService/sound.mp3
        """.trimIndent()
            try {
                cfgFile.writeText(initialContent)
                Log.i("PiListenerService", "config.ini criado")
            } catch (e: Exception) {
                Log.e("PiListenerService", "Erro ao criar config.ini: ${e.message}")
                throw e
            }
        }
        val props = Properties()
        try {
            cfgFile.readLines().forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) { props[parts[0].trim()] = parts[1].trim() }
            }
            Log.i("PiListenerService", "config.ini carregado")
        } catch (e: Exception) {
            Log.e("PiListenerService", "Erro ao ler config.ini: ${e.message}")
            throw e
        }
        return props
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
