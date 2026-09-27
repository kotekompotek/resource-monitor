package com.kotekompotek.resourcemonitor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_SETTINGS_CHANGED = "com.kotekompotek.resourcemonitor.SETTINGS_CHANGED"
        /** Public URL of the hosted privacy policy. Replace before publishing to Google Play
         * and paste the same link into Play Console (store listing) and the Data safety section. */
        const val PRIVACY_POLICY_URL = "https://kotekompotek.github.io/resource-monitor/privacy-policy.html"
    }

    private lateinit var cbShowDisk: CheckBox
    private lateinit var etPrecDisk: EditText
    private lateinit var cbRevDisk: CheckBox
    private lateinit var cbGrDisk: CheckBox

    private lateinit var cbShowRam: CheckBox
    private lateinit var etPrecRam: EditText
    private lateinit var cbRevRam: CheckBox
    private lateinit var cbGrRam: CheckBox

    private lateinit var cbShowCpu: CheckBox
    private lateinit var etPrecCpu: EditText
    private lateinit var cbRevCpu: CheckBox
    private lateinit var cbGrCpu: CheckBox

    private lateinit var cbShowBattery: CheckBox
    private lateinit var etPrecBattery: EditText
    private lateinit var cbRevBattery: CheckBox
    private lateinit var cbGrBattery: CheckBox

    private lateinit var cbShowCurrent: CheckBox
    private lateinit var etPrecCurrent: EditText
    private lateinit var cbRevCurrent: CheckBox
    private lateinit var cbGrCurrent: CheckBox

    private lateinit var cbShowNet: CheckBox
    private lateinit var etPrecNet: EditText
    private lateinit var cbRevNet: CheckBox
    private lateinit var cbGrNet: CheckBox

    private lateinit var cbShowPing: CheckBox
    private lateinit var etPrecPing: EditText
    private lateinit var cbRevPing: CheckBox
    private lateinit var cbGrPing: CheckBox

    private lateinit var rgDiskDisplay: RadioGroup
    private lateinit var rgRamDisplay: RadioGroup
    private lateinit var sbWidth: SeekBar
    private lateinit var sbHeight: SeekBar
    private lateinit var sbScale: SeekBar
    private lateinit var tvWidthLabel: TextView
    private lateinit var tvHeightLabel: TextView
    private lateinit var tvScaleLabel: TextView
    private lateinit var btnStart: Button

    /** System overlay-settings screen (replaces deprecated onActivityResult). */
    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (checkOverlayPermission()) {
                proceedAfterOverlayGranted()
            } else {
                Toast.makeText(
                    this,
                    "Overlay permission is required to show the monitor widget.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    /** Android 13+ notification runtime permission. The service runs with or without it. */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startFloatingService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadSettings()
        setupListeners()
        updateButtonText()
    }

    override fun onResume() {
        super.onResume()
        updateButtonText()
    }

    private fun initViews() {
        cbShowDisk = findViewById(R.id.cbShowDisk); etPrecDisk = findViewById(R.id.etPrecDisk); cbRevDisk = findViewById(R.id.cbRevDisk); cbGrDisk = findViewById(R.id.cbGrDisk)
        cbShowRam = findViewById(R.id.cbShowRam); etPrecRam = findViewById(R.id.etPrecRam); cbRevRam = findViewById(R.id.cbRevRam); cbGrRam = findViewById(R.id.cbGrRam)
        cbShowCpu = findViewById(R.id.cbShowCpu); etPrecCpu = findViewById(R.id.etPrecCpu); cbRevCpu = findViewById(R.id.cbRevCpu); cbGrCpu = findViewById(R.id.cbGrCpu)
        cbShowBattery = findViewById(R.id.cbShowBattery); etPrecBattery = findViewById(R.id.etPrecBattery); cbRevBattery = findViewById(R.id.cbRevBattery); cbGrBattery = findViewById(R.id.cbGrBattery)
        cbShowCurrent = findViewById(R.id.cbShowCurrent); etPrecCurrent = findViewById(R.id.etPrecCurrent); cbRevCurrent = findViewById(R.id.cbRevCurrent); cbGrCurrent = findViewById(R.id.cbGrCurrent)
        cbShowNet = findViewById(R.id.cbShowNet); etPrecNet = findViewById(R.id.etPrecNet); cbRevNet = findViewById(R.id.cbRevNet); cbGrNet = findViewById(R.id.cbGrNet)
        cbShowPing = findViewById(R.id.cbShowPing); etPrecPing = findViewById(R.id.etPrecPing); cbRevPing = findViewById(R.id.cbRevPing); cbGrPing = findViewById(R.id.cbGrPing)

        rgDiskDisplay = findViewById(R.id.rgDiskDisplay); rgRamDisplay = findViewById(R.id.rgRamDisplay)
        sbWidth = findViewById(R.id.sbWidth); sbHeight = findViewById(R.id.sbHeight); sbScale = findViewById(R.id.sbScale)
        tvWidthLabel = findViewById(R.id.tvWidthLabel); tvHeightLabel = findViewById(R.id.tvHeightLabel); tvScaleLabel = findViewById(R.id.tvScaleLabel)
        btnStart = findViewById(R.id.btnStart)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("monitor_prefs", Context.MODE_PRIVATE)

        cbShowDisk.isChecked = prefs.getBoolean("show_disk", true); etPrecDisk.setText(prefs.getInt("prec_disk", 2).toString()); cbRevDisk.isChecked = prefs.getBoolean("rev_disk", false); cbGrDisk.isChecked = prefs.getBoolean("gr_disk", true)
        cbShowRam.isChecked = prefs.getBoolean("show_ram", true); etPrecRam.setText(prefs.getInt("prec_ram", 2).toString()); cbRevRam.isChecked = prefs.getBoolean("rev_ram", false); cbGrRam.isChecked = prefs.getBoolean("gr_ram", true)
        cbShowCpu.isChecked = prefs.getBoolean("show_cpu", true); etPrecCpu.setText(prefs.getInt("prec_cpu", 1).toString()); cbRevCpu.isChecked = prefs.getBoolean("rev_cpu", false); cbGrCpu.isChecked = prefs.getBoolean("gr_cpu", true)
        cbShowBattery.isChecked = prefs.getBoolean("show_battery", true); etPrecBattery.setText(prefs.getInt("prec_battery", 1).toString()); cbRevBattery.isChecked = prefs.getBoolean("rev_battery", false); cbGrBattery.isChecked = prefs.getBoolean("gr_battery", true)
        cbShowCurrent.isChecked = prefs.getBoolean("show_current", true); etPrecCurrent.setText(prefs.getInt("prec_current", 3).toString()); cbRevCurrent.isChecked = prefs.getBoolean("rev_current", false); cbGrCurrent.isChecked = prefs.getBoolean("gr_current", true)
        cbShowNet.isChecked = prefs.getBoolean("show_net", true); etPrecNet.setText(prefs.getInt("prec_net", 2).toString()); cbRevNet.isChecked = prefs.getBoolean("rev_net", false); cbGrNet.isChecked = prefs.getBoolean("gr_net", true)
        cbShowPing.isChecked = prefs.getBoolean("show_ping", true); etPrecPing.setText(prefs.getInt("prec_ping", 0).toString()); cbRevPing.isChecked = prefs.getBoolean("rev_ping", false); cbGrPing.isChecked = prefs.getBoolean("gr_ping", true)

        rgDiskDisplay.check(prefs.getInt("disk_display_id", R.id.rbDiskValue))
        rgRamDisplay.check(prefs.getInt("ram_display_id", R.id.rbRamValue))

        val width = prefs.getInt("widget_width", 150); sbWidth.progress = width; tvWidthLabel.text = "Widget Width: ${width}dp"
        val height = prefs.getInt("graph_height", 30); sbHeight.progress = height; tvHeightLabel.text = "Graph Height: ${height}dp"
        val scale = prefs.getFloat("widget_scale", 1.0f); sbScale.progress = (scale * 100).toInt(); tvScaleLabel.text = "Widget Scale: ${String.format(Locale.US, "%.1fx", scale)}"
    }

    private fun setupListeners() {
        val onChangeListener = CompoundButton.OnCheckedChangeListener { _, _ -> saveAndNotify() }
        listOf(cbShowDisk, cbRevDisk, cbGrDisk, cbShowRam, cbRevRam, cbGrRam, cbShowCpu, cbRevCpu, cbGrCpu,
               cbShowBattery, cbRevBattery, cbGrBattery, cbShowCurrent, cbRevCurrent, cbGrCurrent,
               cbShowNet, cbRevNet, cbGrNet, cbShowPing, cbRevPing, cbGrPing).forEach { it.setOnCheckedChangeListener(onChangeListener) }

        rgDiskDisplay.setOnCheckedChangeListener { _, _ -> saveAndNotify() }
        rgRamDisplay.setOnCheckedChangeListener { _, _ -> saveAndNotify() }

        val seekBars = mapOf(sbWidth to 50, sbHeight to 10, sbScale to 50)
        seekBars.forEach { (sb, min) ->
            sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val value = p.coerceAtLeast(min)
                    if (sb == sbScale) tvScaleLabel.text = "Widget Scale: ${String.format(Locale.US, "%.1fx", value/100f)}"
                    else if (sb == sbWidth) tvWidthLabel.text = "Widget Width: ${value}dp"
                    else tvHeightLabel.text = "Graph Height: ${value}dp"
                    if (fromUser) saveAndNotify()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }

        findViewById<Button>(R.id.btnWidthMinus).setOnClickListener { sbWidth.progress -= 5; saveAndNotify() }
        findViewById<Button>(R.id.btnWidthPlus).setOnClickListener { sbWidth.progress += 5; saveAndNotify() }
        findViewById<Button>(R.id.btnHeightMinus).setOnClickListener { sbHeight.progress -= 2; saveAndNotify() }
        findViewById<Button>(R.id.btnHeightPlus).setOnClickListener { sbHeight.progress += 2; saveAndNotify() }
        findViewById<Button>(R.id.btnScaleMinus).setOnClickListener { sbScale.progress -= 10; saveAndNotify() }
        findViewById<Button>(R.id.btnScalePlus).setOnClickListener { sbScale.progress += 10; saveAndNotify() }

        setupPrecButtons(R.id.btnPrecDiskMinus, R.id.btnPrecDiskPlus, etPrecDisk)
        setupPrecButtons(R.id.btnPrecRamMinus, R.id.btnPrecRamPlus, etPrecRam)
        setupPrecButtons(R.id.btnPrecCpuMinus, R.id.btnPrecCpuPlus, etPrecCpu)
        setupPrecButtons(R.id.btnPrecBatteryMinus, R.id.btnPrecBatteryPlus, etPrecBattery)
        setupPrecButtons(R.id.btnPrecCurrentMinus, R.id.btnPrecCurrentPlus, etPrecCurrent)
        setupPrecButtons(R.id.btnPrecNetMinus, R.id.btnPrecNetPlus, etPrecNet)
        setupPrecButtons(R.id.btnPrecPingMinus, R.id.btnPrecPingPlus, etPrecPing)

        btnStart.setOnClickListener {
            if (FloatingService.isRunning) {
                stopService(Intent(this, FloatingService::class.java))
            } else {
                ensureOverlayAndStart()
            }
            btnStart.postDelayed({ updateButtonText() }, 300)
        }

        findViewById<Button>(R.id.btnPrivacy).setOnClickListener { showPrivacyPolicy() }
    }

    private fun setupPrecButtons(minusId: Int, plusId: Int, editText: EditText) {
        findViewById<Button>(minusId).setOnClickListener {
            val v = (editText.text.toString().toIntOrNull() ?: 0) - 1
            editText.setText(v.coerceAtLeast(0).toString())
            saveAndNotify()
        }
        findViewById<Button>(plusId).setOnClickListener {
            val v = (editText.text.toString().toIntOrNull() ?: 0) + 1
            editText.setText(v.coerceIn(0, 5).toString())
            saveAndNotify()
        }
    }

    private fun updateButtonText() {
        btnStart.text = if (FloatingService.isRunning) "Stop Monitor" else "Start Monitor"
    }

    private fun saveAndNotify() {
        val prefs = getSharedPreferences("monitor_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("show_disk", cbShowDisk.isChecked); putInt("prec_disk", etPrecDisk.text.toString().toIntOrNull() ?: 2); putBoolean("rev_disk", cbRevDisk.isChecked); putBoolean("gr_disk", cbGrDisk.isChecked)
            putBoolean("show_ram", cbShowRam.isChecked); putInt("prec_ram", etPrecRam.text.toString().toIntOrNull() ?: 2); putBoolean("rev_ram", cbRevRam.isChecked); putBoolean("gr_ram", cbGrRam.isChecked)
            putBoolean("show_cpu", cbShowCpu.isChecked); putInt("prec_cpu", etPrecCpu.text.toString().toIntOrNull() ?: 1); putBoolean("rev_cpu", cbRevCpu.isChecked); putBoolean("gr_cpu", cbGrCpu.isChecked)
            putBoolean("show_battery", cbShowBattery.isChecked); putInt("prec_battery", etPrecBattery.text.toString().toIntOrNull() ?: 1); putBoolean("rev_battery", cbRevBattery.isChecked); putBoolean("gr_battery", cbGrBattery.isChecked)
            putBoolean("show_current", cbShowCurrent.isChecked); putInt("prec_current", etPrecCurrent.text.toString().toIntOrNull() ?: 3); putBoolean("rev_current", cbRevCurrent.isChecked); putBoolean("gr_current", cbGrCurrent.isChecked)
            putBoolean("show_net", cbShowNet.isChecked); putInt("prec_net", etPrecNet.text.toString().toIntOrNull() ?: 2); putBoolean("rev_net", cbRevNet.isChecked); putBoolean("gr_net", cbGrNet.isChecked)
            putBoolean("show_ping", cbShowPing.isChecked); putInt("prec_ping", etPrecPing.text.toString().toIntOrNull() ?: 0); putBoolean("rev_ping", cbRevPing.isChecked); putBoolean("gr_ping", cbGrPing.isChecked)

            putInt("disk_display_id", rgDiskDisplay.checkedRadioButtonId); putInt("ram_display_id", rgRamDisplay.checkedRadioButtonId)
            putInt("widget_width", sbWidth.progress.coerceAtLeast(50)); putInt("graph_height", sbHeight.progress.coerceAtLeast(10))
            putFloat("widget_scale", sbScale.progress.coerceAtLeast(50) / 100.0f)
            putInt("disk_mode", if(rgDiskDisplay.checkedRadioButtonId == R.id.rbDiskValue) 0 else if(rgDiskDisplay.checkedRadioButtonId == R.id.rbDiskPercent) 1 else 2)
            putInt("ram_mode", if(rgRamDisplay.checkedRadioButtonId == R.id.rbRamValue) 0 else if(rgRamDisplay.checkedRadioButtonId == R.id.rbRamPercent) 1 else 2)
            apply()
        }
        if (FloatingService.isRunning) startService(Intent(this, FloatingService::class.java).apply { action = ACTION_SETTINGS_CHANGED })
    }

    // --- Google Play policy: prominent in-app disclosure before the overlay permission ---

    private fun ensureOverlayAndStart() {
        if (checkOverlayPermission()) proceedAfterOverlayGranted()
        else showOverlayDisclosure()
    }

    private fun showOverlayDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Display over other apps")
            .setMessage(
                "Resource monitor shows a small movable widget on top of other apps " +
                "with your device's CPU, RAM, disk, battery, network and ping readings.\n\n" +
                "The widget never pretends to be part of the system or another app, " +
                "does not intercept your taps in other apps, and can be stopped at any time " +
                "from this screen, from its notification, or by holding it in the red zone.\n\n" +
                "On the next screen, please allow \"Display over other apps\"."
            )
            .setPositiveButton("Continue") { _, _ -> requestOverlayPermission() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun proceedAfterOverlayGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            // The persistent notification keeps you informed about the running monitor.
            // The service works even if you deny it.
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startFloatingService()
        }
    }

    private fun showPrivacyPolicy() {
        AlertDialog.Builder(this)
            .setTitle("Privacy Policy")
            .setMessage(
                "Resource monitor processes all data on your device and sends nothing anywhere.\n\n" +
                "• CPU, RAM, disk, battery and traffic counters are read from Android system APIs and shown only in the overlay.\n" +
                "• The Ping feature sends one ICMP echo per 5 seconds to 8.8.8.8 to measure latency. No personal data is included.\n" +
                "• No accounts, no analytics, no ads, no third-party SDKs.\n" +
                "• Settings are stored only in this device's app preferences.\n\n" +
                "Full text: $PRIVACY_POLICY_URL"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun checkOverlayPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            overlaySettingsLauncher.launch(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun startFloatingService() {
        ContextCompat.startForegroundService(this, Intent(this, FloatingService::class.java))
    }
}
