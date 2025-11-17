package com.example.pushapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.widget.Button
import android.widget.TextView
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.switchmaterial.SwitchMaterial
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import android.widget.Toast
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class MainActivity : ComponentActivity() {

    private val channelId = "battery_channel"
    private lateinit var prefs: SharedPreferences
    private lateinit var switchMaster: SwitchMaterial
    private lateinit var textDeviceModel: TextView
    private lateinit var textBatteryLevel: TextView
    private lateinit var buttonSettings: ImageButton
    private lateinit var buttonClear: Button
    private lateinit var recyclerLogs: RecyclerView
    private lateinit var logAdapter: LogAdapter
    private var currentBatteryPct: Int = -1
    private val handler = Handler(Looper.getMainLooper())
    private var autoScheduled = false
    private val autoRunnable = Runnable { executeIfEnabled() }

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            postBatteryNotification()
        }
    }

    private val requestCallPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCall()
        }
    }

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            sendSms()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        setContentView(R.layout.activity_main)

        bindViews()
        initFromPrefs()
        attachListeners()

        createNotificationChannel()
        if (switchMaster.isChecked) {
            scheduleAutoExecute()
        }

        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun bindViews() {
        switchMaster = findViewById(R.id.switch_master)
        textDeviceModel = findViewById(R.id.text_device_model)
        textBatteryLevel = findViewById(R.id.text_battery_level)
        buttonSettings = findViewById(R.id.button_settings)
        buttonClear = findViewById(R.id.button_clear)
        recyclerLogs = findViewById(R.id.recycler_logs)
        
        // Setup RecyclerView
        recyclerLogs.layoutManager = LinearLayoutManager(this)
        logAdapter = LogAdapter(emptyList())
        recyclerLogs.adapter = logAdapter
        
        updateDeviceInfo()
    }

    private fun initFromPrefs() {
        switchMaster.isChecked = prefs.getBoolean("master", true)
        refreshLogs()
    }

    private fun attachListeners() {
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("master", isChecked).apply()
            if (isChecked) scheduleAutoExecute() else cancelAutoExecute()
        }
        buttonSettings.setOnClickListener { startActivity(android.content.Intent(this, SettingsActivity::class.java)) }
        buttonClear.setOnClickListener {
            LogStore.clear(this)
            refreshLogs()
        }
    }

    private fun maybeRequestNotificationPermissionAndPost() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
                PackageManager.PERMISSION_GRANTED -> postBatteryNotification()
                else -> requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            postBatteryNotification()
        }
    }

    private fun postBatteryNotification() {
        val level = getCurrentBatteryPct()
        val title = "当前电量"
        val text = "电量：$level%"

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            with(NotificationManagerCompat.from(this)) { notify(1001, notification) }
            LogStore.append(this, "${now()} 通知 电量=$level%")
            refreshLogs()
        } catch (e: SecurityException) {
            // Permission denied, ignore
        }
    }

    private fun maybeRequestCallPermissionAndDial() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)) {
            PackageManager.PERMISSION_GRANTED -> startCall()
            else -> requestCallPermission.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun startCall() {
        val number = prefs.getString("phoneCall", "")?.trim().orEmpty()
        if (number.isEmpty()) return
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
        startActivity(intent)
        LogStore.append(this, "${now()} 拨打电话 $number")
        refreshLogs()
    }

    private fun maybeRequestSmsPermissionAndSend() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)) {
            PackageManager.PERMISSION_GRANTED -> sendSms()
            else -> requestSmsPermission.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun sendSms() {
        val number = prefs.getString("phoneSms", "")?.trim().orEmpty()
        val original = "设备：{MODEL}，电量：{BATTERY}% 时间：{TIME}"
        var text = resolveTemplate(original)
        if (number.isEmpty() || text.isEmpty()) return
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
        sms.sendTextMessage(number, null, text, null, null)
        LogStore.append(this, "${now()} 发送短信 $number 内容=$text")
        refreshLogs()
    }

    private fun executeIfEnabled(force: Boolean = false) {
        val masterOn = switchMaster.isChecked || force
        if (!masterOn) return
        if (prefs.getBoolean("notify", true)) maybeRequestNotificationPermissionAndPost()
        if (prefs.getBoolean("call", false)) maybeRequestCallPermissionAndDial()
        if (prefs.getBoolean("sms", false)) maybeRequestSmsPermissionAndSend()
        if (prefs.getBoolean("webhook", false)) sendWebhook()
        cancelAutoExecute()
    }

    private fun scheduleAutoExecute() {
        cancelAutoExecute()
        autoScheduled = true
        handler.postDelayed(autoRunnable, 3000)
    }

    private fun cancelAutoExecute() {
        if (autoScheduled) {
            handler.removeCallbacks(autoRunnable)
            autoScheduled = false
        }
    }

    private fun getCurrentBatteryPct(): Int {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                return (level * 100) / scale
            }
        }
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun resolveTemplate(text: String): String {
        val model = "${Build.MANUFACTURER} ${Build.MODEL}"
        val level = getCurrentBatteryPct().toString()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        return text
            .replace("{MODEL}", model)
            .replace("{MANUFACTURER}", Build.MANUFACTURER)
            .replace("{BRAND}", Build.BRAND)
            .replace("{BATTERY}", level)
            .replace("{TIME}", now)
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                currentBatteryPct = (level * 100) / scale
                updateDeviceInfo()
            }
        }
    }

    private fun updateDeviceInfo() {
        val model = "${Build.MANUFACTURER} ${Build.MODEL}"
        val pct = if (currentBatteryPct >= 0) currentBatteryPct else getCurrentBatteryPct()
        textDeviceModel.text = model
        textBatteryLevel.text = "$pct%"
    }

    private fun sendWebhook() {
        val urlStr = prefs.getString("webhookUrl", "")?.trim().orEmpty()
        if (urlStr.isEmpty()) return
        val model = "${Build.MANUFACTURER} ${Build.MODEL}"
        val pct = getCurrentBatteryPct()
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val base = "{\"msg_type\":\"text\",\"content\":{\"text\":\"设备：$model 电量：$pct% 时间：$now\"}}"
        Thread {
            try {
                val finalUrl = if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) urlStr else "http://$urlStr"
                val url = URL(finalUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val signOn = prefs.getBoolean("webhookSign", false)
                val secret = prefs.getString("webhookSecret", "") ?: ""
                val body = if (signOn && secret.isNotEmpty()) {
                    val ts = (System.currentTimeMillis() / 1000).toString()
                    val sign = signFeishu(ts, secret)
                    "{\"timestamp\":\"$ts\",\"sign\":\"$sign\",${base.substring(1)}"
                } else base
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                try {
                    if (code in 200..299) {
                        val resp = conn.inputStream.use { String(it.readBytes()) }
                        val status = if (resp.contains("\"code\":0")) "成功" else resp
                        handler.post { Toast.makeText(this, "Webhook响应：$status", Toast.LENGTH_SHORT).show() }
                        LogStore.append(this, "${now()} Webhook 成功 code=$code")
                    } else {
                        conn.errorStream?.use { String(it.readBytes()) }
                        handler.post { Toast.makeText(this, "Webhook失败：$code", Toast.LENGTH_SHORT).show() }
                        LogStore.append(this, "${now()} Webhook 失败 code=$code")
                    }
                    conn.disconnect()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                handler.post { Toast.makeText(this, "Webhook错误", Toast.LENGTH_SHORT).show() }
                LogStore.append(this, "${now()} Webhook 错误")
            }
            handler.post { refreshLogs() }
        }.start()
    }

    private fun refreshLogs() {
        val data = LogStore.getAll(this)
        logAdapter = LogAdapter(data)
        recyclerLogs.adapter = logAdapter
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    private fun signFeishu(timestamp: String, secret: String): String {
        val data = "$timestamp\n$secret"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val raw = mac.doFinal(data.toByteArray())
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "电量通知"
            val descriptionText = "应用打开时推送当前电量"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}