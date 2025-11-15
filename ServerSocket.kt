class DoorbellListenerService : Service() {
    private val PORT = 9000
    private var serverThread: Thread? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "Doorbell:Wake")
        startServer()
    }

    private fun startServer() {
        serverThread = Thread {
            val server = ServerSocket(PORT)
            while (!Thread.currentThread().isInterrupted) {
                val sock = server.accept()
                val input = sock.getInputStream().bufferedReader().readLine()
                // assumir que o comando é simples, e.g. "RING"
                if (input != null && input.trim() == "RING") {
                    handleRing()
                }
                sock.close()
            }
            server.close()
        }
        serverThread?.start()
    }

    private fun handleRing() {
        // 1) Wake + unlock (ver estratégias abaixo)
        acquireWakeAndUnlock()
        // 2) Tocar mp3
        playMp3()
        // 3) Abrir Activity que mostra a página HTML
        val i = Intent(this, DisplayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("url", "http://www.sapo.pt")
          //"file:///android_asset/intercom.html") // ou http://<ip>...
        }
        startActivity(i)
    }

    private fun acquireWakeAndUnlock() {
        if (!wakeLock.isHeld) wakeLock.acquire(10_000) // 10s ou até liberares
        // Pedir dismiss do keyguard via Activity -> fazemos via intent que a Activity processa
        val i = Intent(this, DisplayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SHOW_WHEN_LOCKED or Intent.FLAG_ACTIVITY_TURN_SCREEN_ON)
            putExtra("from_service", true)
        }
        startActivity(i)
    }

    private fun playMp3() {
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
        mediaPlayer = MediaPlayer.create(this, R.raw.ringtone) // coloca ringtone.mp3 em res/raw
        mediaPlayer?.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        serverThread?.interrupt()
        mediaPlayer?.release()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    // restantes overrides onBind etc...
}
