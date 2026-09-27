package com.kotekompotek.resourcemonitor

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.net.TrafficStats
import android.util.TypedValue
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread

class FloatingService : Service() {

    companion object {
        const val ACTION_STOP = "com.kotekompotek.resourcemonitor.STOP"
        private const val NOTIF_CHANNEL_ID = "monitor_fg"
        private const val NOTIF_ID = 1
        /** Ping at most once per interval to save battery and network (Play policy: Device and Network Abuse). */
        private const val PING_INTERVAL_MS = 5000L

        /** Tracked locally instead of the deprecated ActivityManager.getRunningServices(). */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var removeView: View
    private lateinit var tvRemoveHint: TextView

    private lateinit var tvFreeSpace: TextView
    private lateinit var graphView: SpaceGraphView
    private lateinit var tvFreeRam: TextView
    private lateinit var graphRamView: SpaceGraphView
    private lateinit var tvCpuFreq: TextView
    private lateinit var graphCpuView: SpaceGraphView
    private lateinit var tvBatteryTemp: TextView
    private lateinit var graphBatteryView: SpaceGraphView
    private lateinit var tvBatteryCurrent: TextView
    private lateinit var graphCurrentView: SpaceGraphView
    private lateinit var tvNetSpeed: TextView
    private lateinit var graphNetView: SpaceGraphView
    private lateinit var tvPing: TextView
    private lateinit var graphPingView: SpaceGraphView

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var activityManager: ActivityManager
    private val memoryInfo = ActivityManager.MemoryInfo()
    private lateinit var batteryManager: BatteryManager

    private var lastNetBytes: Long = 0; private var lastNetTime: Long = 0
    private var currentDisplaySpeed = 0.0; private var startDisplaySpeed = 0.0; private var targetDisplaySpeed = 0.0; private var netAnimationStartTime = 0L
    private var currentDisplayTemp = 0.0; private var startDisplayTemp = 0.0; private var targetDisplayTemp = 0.0; private var tempAnimationStartTime = 0L; private var lastMeasuredTemp = -1.0
    private var currentPing = "..."; private var lastPingVal = 0.0
    private var lastPingTime = 0L
    private var foregroundStarted = false

    /** Pause metric collection while the screen is off to save battery. */
    @Volatile
    private var screenOn = true
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> screenOn = false
                Intent.ACTION_SCREEN_ON -> screenOn = true
            }
        }
    }

    // Settings
    private var showDisk = true; private var showRam = true; private var showCpu = true; private var showBattery = true; private var showCurrent = true; private var showNet = true; private var showPing = true
    private var diskMode = 0; private var ramMode = 0; private var widgetWidth = 150; private var graphHeight = 30; private var widgetScale = 1.0f
    private var revDisk = false; private var revRam = false; private var revCpu = false; private var revBattery = false; private var revCurrent = false; private var revNet = false; private var revPing = false
    private var grDisk = true; private var grRam = true; private var grCpu = true; private var grBattery = true; private var grCurrent = true; private var grNet = true; private var grPing = true
    private var precDisk = 2; private var precRam = 2; private var precCpu = 0; private var precBattery = 1; private var precCurrent = 3; private var precNet = 2; private var precPing = 0

    private var isInsideDeleteZone = false
    private val deleteDelay = 3000L
    private val deleteRunnable = Runnable { stopSelf() }

    private val updateRunnable = object : Runnable {
        override fun run() { updateMetrics(); handler.postDelayed(this, 100) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            MainActivity.ACTION_SETTINGS_CHANGED -> { loadSettings(); applySettingsToUi() }
        }
        // Re-assert foreground status on every start (required shortly after startForegroundService()).
        startForegroundSafe()
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        lastNetBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
        lastNetTime = System.currentTimeMillis()
        registerScreenReceiver()
        loadSettings(); createFloatingView(); createRemoveView(); applySettingsToUi()
        startForegroundSafe()
        handler.post(updateRunnable)
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun startForegroundSafe() {
        if (foregroundStarted) return
        foregroundStarted = true
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(NOTIF_CHANNEL_ID, "Resource monitor", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, FloatingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Resource monitor running")
            .setContentText("Overlay widget is active. Tap to open settings.")
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("monitor_prefs", Context.MODE_PRIVATE)
        showDisk = prefs.getBoolean("show_disk", true); showRam = prefs.getBoolean("show_ram", true)
        showCpu = prefs.getBoolean("show_cpu", true); showBattery = prefs.getBoolean("show_battery", true)
        showCurrent = prefs.getBoolean("show_current", true); showNet = prefs.getBoolean("show_net", true); showPing = prefs.getBoolean("show_ping", true)
        diskMode = prefs.getInt("disk_mode", 0); ramMode = prefs.getInt("ram_mode", 0)
        widgetWidth = prefs.getInt("widget_width", 150); graphHeight = prefs.getInt("graph_height", 30); widgetScale = prefs.getFloat("widget_scale", 1.0f)
        revDisk = prefs.getBoolean("rev_disk", false); revRam = prefs.getBoolean("rev_ram", false); revCpu = prefs.getBoolean("rev_cpu", false); revBattery = prefs.getBoolean("rev_battery", false); revCurrent = prefs.getBoolean("rev_current", false); revNet = prefs.getBoolean("rev_net", false); revPing = prefs.getBoolean("rev_ping", false)
        grDisk = prefs.getBoolean("gr_disk", true); grRam = prefs.getBoolean("gr_ram", true); grCpu = prefs.getBoolean("gr_cpu", true); grBattery = prefs.getBoolean("gr_battery", true); grCurrent = prefs.getBoolean("gr_current", true); grNet = prefs.getBoolean("gr_net", true); grPing = prefs.getBoolean("gr_ping", true)
        precDisk = prefs.getInt("prec_disk", 2); precRam = prefs.getInt("prec_ram", 2); precCpu = prefs.getInt("prec_cpu", 0); precBattery = prefs.getInt("prec_battery", 1); precCurrent = prefs.getInt("prec_current", 3); precNet = prefs.getInt("prec_net", 2); precPing = prefs.getInt("prec_ping", 0)
    }

    private fun applySettingsToUi() {
        if (!::floatingView.isInitialized) return

        // Сбрасываем границы для всех графиков (авто-масштабирование), кроме Сети и Пинга
        listOf(graphView, graphRamView, graphCpuView, graphBatteryView, graphCurrentView).forEach {
            it.minY = null
            it.maxY = null
        }

        // Фиксируем базу 0 и минимум шкалы для Сети и Пинга, чтобы графики были читаемыми
        graphNetView.minY = 0.0
        graphNetView.maxY = 1024.0 // 1 МБ/с (1024 КБ/с)

        graphPingView.minY = 0.0
        graphPingView.maxY = 100.0 // 100 мс

        tvFreeSpace.visibility = if (showDisk) View.VISIBLE else View.GONE; graphView.visibility = if (showDisk && grDisk) View.VISIBLE else View.GONE; graphView.isReversed = revDisk
        tvFreeRam.visibility = if (showRam) View.VISIBLE else View.GONE; graphRamView.visibility = if (showRam && grRam) View.VISIBLE else View.GONE; graphRamView.isReversed = revRam
        tvCpuFreq.visibility = if (showCpu) View.VISIBLE else View.GONE; graphCpuView.visibility = if (showCpu && grCpu) View.VISIBLE else View.GONE; graphCpuView.isReversed = revCpu
        tvBatteryTemp.visibility = if (showBattery) View.VISIBLE else View.GONE; graphBatteryView.visibility = if (showBattery && grBattery) View.VISIBLE else View.GONE; graphBatteryView.isReversed = revBattery
        tvBatteryCurrent.visibility = if (showCurrent) View.VISIBLE else View.GONE; graphCurrentView.visibility = if (showCurrent && grCurrent) View.VISIBLE else View.GONE; graphCurrentView.isReversed = revCurrent
        tvNetSpeed.visibility = if (showNet) View.VISIBLE else View.GONE; graphNetView.visibility = if (showNet && grNet) View.VISIBLE else View.GONE; graphNetView.isReversed = revNet
        tvPing.visibility = if (showPing) View.VISIBLE else View.GONE; graphPingView.visibility = if (showPing && grPing) View.VISIBLE else View.GONE; graphPingView.isReversed = revPing

        val baseSize = 12f * widgetScale
        listOf(tvFreeSpace, tvFreeRam, tvCpuFreq, tvBatteryTemp, tvBatteryCurrent, tvNetSpeed, tvPing).forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSize) }

        val density = resources.displayMetrics.density
        val w = (widgetWidth * density * widgetScale).toInt()
        val h = (graphHeight * density * widgetScale).toInt()
        val m = (2 * density * widgetScale).toInt(); val ml = (6 * density * widgetScale).toInt()

        floatingView.layoutParams.width = w
        listOf(graphView, graphRamView, graphCpuView, graphBatteryView, graphCurrentView, graphNetView, graphPingView).forEach {
            it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h).apply { topMargin = m }
        }
        listOf(tvFreeRam, tvCpuFreq, tvBatteryTemp, tvBatteryCurrent, tvNetSpeed).forEach { (it.layoutParams as? LinearLayout.LayoutParams)?.topMargin = ml }

        floatingView.post { try { windowManager.updateViewLayout(floatingView, floatingView.layoutParams) } catch (e: Exception) {} }
    }

    private fun createFloatingView() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        params.gravity = Gravity.TOP or Gravity.START; params.x = 0; params.y = 100
        tvFreeSpace = floatingView.findViewById(R.id.tvFreeSpace); graphView = floatingView.findViewById(R.id.graphView)
        tvFreeRam = floatingView.findViewById(R.id.tvFreeRam); graphRamView = floatingView.findViewById(R.id.graphRamView)
        tvCpuFreq = floatingView.findViewById(R.id.tvCpuLoad); graphCpuView = floatingView.findViewById(R.id.graphCpuView)
        tvBatteryTemp = floatingView.findViewById(R.id.tvBatteryTemp); graphBatteryView = floatingView.findViewById(R.id.graphBatteryView)
        tvBatteryCurrent = floatingView.findViewById(R.id.tvBatteryCurrent); graphCurrentView = floatingView.findViewById(R.id.graphCurrentView)
        tvNetSpeed = floatingView.findViewById(R.id.tvNetSpeed); graphNetView = floatingView.findViewById(R.id.graphNetView)
        tvPing = floatingView.findViewById(R.id.tvPing); graphPingView = floatingView.findViewById(R.id.graphPingView)
        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var ix: Int = 0; private var iy: Int = 0; private var itx: Float = 0f; private var ity: Float = 0f
            override fun onTouch(v: View?, e: MotionEvent?): Boolean {
                when (e?.action) {
                    MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; itx = e.rawX; ity = e.rawY; removeView.visibility = View.VISIBLE; return true }
                    MotionEvent.ACTION_UP -> { removeView.visibility = View.GONE; cancelDeletion(); return true }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = ix + (e.rawX - itx).toInt(); params.y = iy + (e.rawY - ity).toInt()
                        try { windowManager.updateViewLayout(floatingView, params) } catch(ex: Exception) {}
                        if (e.rawY > getScreenHeight() * 0.8) { if (!isInsideDeleteZone) startDeletionCountdown() } else cancelDeletion()
                        return true
                    }
                }
                return false
            }
        })
        windowManager.addView(floatingView, params)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig); if (::floatingView.isInitialized) floatingView.post {
            val p = floatingView.layoutParams as WindowManager.LayoutParams; val sw = getScreenWidth(); val sh = getScreenHeight()
            if (p.x + floatingView.width > sw) p.x = (sw - floatingView.width).coerceAtLeast(0)
            if (p.y + floatingView.height > sh) p.y = (sh - floatingView.height).coerceAtLeast(0)
            try { windowManager.updateViewLayout(floatingView, p) } catch (e: Exception) {}
        }
    }

    private fun startDeletionCountdown() { isInsideDeleteZone = true; removeView.setBackgroundColor(0xFFFF0000.toInt()); handler.postDelayed(deleteRunnable, deleteDelay) }
    private fun cancelDeletion() { isInsideDeleteZone = false; removeView.setBackgroundColor(0x80FF0000.toInt()); handler.removeCallbacks(deleteRunnable) }
    private fun createRemoveView() {
        removeView = LayoutInflater.from(this).inflate(R.layout.remove_view, null); tvRemoveHint = removeView.findViewById(android.R.id.text1) ?: (removeView as ViewGroup).getChildAt(0) as TextView
        val p = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT); p.gravity = Gravity.BOTTOM; removeView.visibility = View.GONE; windowManager.addView(removeView, p)
    }

    private fun getScreenHeight(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.height()
        } else {
            @Suppress("DEPRECATION")
            Point().apply { windowManager.defaultDisplay.getSize(this) }.y
        }
    }

    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else {
            @Suppress("DEPRECATION")
            Point().apply { windowManager.defaultDisplay.getSize(this) }.x
        }
    }

    private fun easeInOut(t: Double) = t * t * (3.0 - 2.0 * t)

    private fun updateMetrics() {
        // Skip work while the screen is off: the overlay is not visible anyway.
        if (!screenOn) return
        if (showDisk) {
            // Internal storage partition (same userdata volume as external storage on modern devices).
            val stat = try { StatFs(Environment.getDataDirectory().path) } catch(e: Exception) { null }
            if (stat != null) {
                val freeMb = (stat.blockSizeLong.toDouble() * stat.availableBlocksLong) / (1024.0 * 1024.0)
                val percent = (stat.availableBlocksLong.toDouble() / stat.blockCountLong.toDouble().coerceAtLeast(1.0)) * 100.0
                tvFreeSpace.text = when(diskMode) {
                    0 -> String.format(Locale.US, "Disk: %." + precDisk + "f MB", freeMb)
                    1 -> String.format(Locale.US, "Disk: %." + precDisk + "f%%", percent)
                    else -> String.format(Locale.US, "Disk: %." + precDisk + "f MB (%." + precDisk + "f%%)", freeMb, percent)
                }
                graphView.addDataPoint(freeMb)
            }
        }
        if (showRam) {
            activityManager.getMemoryInfo(memoryInfo)
            val freeMb = memoryInfo.availMem.toDouble() / (1024.0 * 1024.0)
            val percent = (memoryInfo.availMem.toDouble() / memoryInfo.totalMem.toDouble().coerceAtLeast(1.0)) * 100.0
            tvFreeRam.text = when(ramMode) {
                0 -> String.format(Locale.US, "RAM: %." + precRam + "f MB", freeMb)
                1 -> String.format(Locale.US, "RAM: %." + precRam + "f%%", percent)
                else -> String.format(Locale.US, "RAM: %." + precRam + "f MB (%." + precRam + "f%%)", freeMb, percent)
            }
            graphRamView.addDataPoint(freeMb)
        }
        if (showCpu) {
            val freq = getAverageCpuFreq().toDouble()
            tvCpuFreq.text = String.format(Locale.US, "CPU: %." + precCpu + "f MHz", freq)
            graphCpuView.addDataPoint(freq)
        }
        if (showBattery) {
            val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            if (temp != lastMeasuredTemp) { startDisplayTemp = currentDisplayTemp; targetDisplayTemp = temp; tempAnimationStartTime = System.currentTimeMillis(); lastMeasuredTemp = temp }
            val t = ((System.currentTimeMillis() - tempAnimationStartTime) / 1000.0).coerceIn(0.0, 1.0)
            currentDisplayTemp = startDisplayTemp + (targetDisplayTemp - startDisplayTemp) * easeInOut(t)
            tvBatteryTemp.text = String.format(Locale.US, "Temp: %." + precBattery + "f °C", currentDisplayTemp)
            graphBatteryView.addDataPoint(currentDisplayTemp)
        }
        if (showCurrent) {
            val currentAmps = -batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000000.0
            tvBatteryCurrent.text = String.format(Locale.US, "Current: %." + precCurrent + "f A", currentAmps)
            graphCurrentView.addDataPoint(currentAmps)
        }
        if (showNet || showPing) {
            val currentBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes(); val currentTime = System.currentTimeMillis(); val diffTime = (currentTime - lastNetTime) / 1000.0
            if (diffTime >= 1.0) {
                targetDisplaySpeed = ((currentBytes - lastNetBytes) / 1024.0) / diffTime
                startDisplaySpeed = currentDisplaySpeed; netAnimationStartTime = currentTime; lastNetBytes = currentBytes; lastNetTime = currentTime
                if (showPing && currentTime - lastPingTime >= PING_INTERVAL_MS) { lastPingTime = currentTime; updatePing() }
            }
            if (showNet) {
                val t = ((System.currentTimeMillis() - netAnimationStartTime) / 1000.0).coerceIn(0.0, 1.0)
                currentDisplaySpeed = startDisplaySpeed + (targetDisplaySpeed - startDisplaySpeed) * easeInOut(t)
                tvNetSpeed.text = if (currentDisplaySpeed < 1024) String.format(Locale.US, "Net: %." + precNet + "f KB/s", currentDisplaySpeed)
                                 else String.format(Locale.US, "Net: %." + precNet + "f MB/s", currentDisplaySpeed / 1024.0)
                graphNetView.addDataPoint(currentDisplaySpeed)
            }
            if (showPing) {
                tvPing.text = if (currentPing == "timeout" || currentPing == "err") "Ping: $currentPing"
                              else String.format(Locale.US, "Ping: %." + precPing + "f ms", lastPingVal)
                graphPingView.addDataPoint(lastPingVal)
            }
        }
    }

    private fun getAverageCpuFreq(): Int {
        return try {
            val cpuFiles = File("/sys/devices/system/cpu/").listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }
            var total = 0L; var count = 0
            cpuFiles?.forEach { f -> val freqFile = File(f, "cpufreq/scaling_cur_freq"); if (freqFile.exists()) { total += freqFile.readText().trim().toLongOrNull() ?: 0L; count++ } }
            if (count > 0) (total / count / 1000).toInt() else 0
        } catch (e: Exception) { 0 }
    }

    private fun updatePing() { thread { try {
        val process = Runtime.getRuntime().exec("ping -c 1 -W 1 8.8.8.8")
        val reader = process.inputStream.bufferedReader()
        var line: String?
        var pVal = 0.0
        var found = false
        while (reader.readLine().also { line = it } != null) {
            val match = Regex("time[= ]+([0-9.]+)").find(line ?: "")
            if (match != null) {
                pVal = match.groupValues[1].toDoubleOrNull() ?: 0.0
                found = true
                break
            }
        }
        process.waitFor()
        if (found) {
            lastPingVal = pVal
            currentPing = pVal.toInt().toString()
        } else {
            lastPingVal = 0.0
            currentPing = "timeout"
        }
    } catch (e: Exception) { currentPing = "err"; lastPingVal = 0.0 } } }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        handler.removeCallbacksAndMessages(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        if (::floatingView.isInitialized) try { windowManager.removeView(floatingView) } catch(e: Exception) {}
        if (::removeView.isInitialized) try { windowManager.removeView(removeView) } catch(e: Exception) {}
    }
}
