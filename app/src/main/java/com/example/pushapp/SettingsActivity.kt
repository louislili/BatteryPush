package com.example.pushapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.telephony.SmsManager

class SettingsActivity : ComponentActivity() {
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var switchMaster: Switch
    private lateinit var switchNotify: Switch
    private lateinit var switchCall: Switch
    private lateinit var switchSms: Switch
    private lateinit var switchWebhook: Switch
    private lateinit var switchWebhookSign: Switch
    private lateinit var editCallPhone: EditText
    private lateinit var editSmsPhone: EditText
    private lateinit var editWebhook: EditText
    private lateinit var editWebhookSecret: EditText
    private lateinit var buttonExecute: Button

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) postBatteryNotification() }

    private val requestCallPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startCall() }

    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) sendSms() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        setContentView(R.layout.activity_settings)
        bindViews()
        initFromPrefs()
        attachListeners()
    }

    private fun bindViews() {
        switchMaster = findViewById(R.id.switch_master)
        switchNotify = findViewById(R.id.switch_notify)
        switchCall = findViewById(R.id.switch_call)
        switchSms = findViewById(R.id.switch_sms)
        switchWebhook = findViewById(R.id.switch_webhook)
        switchWebhookSign = findViewById(R.id.switch_webhook_sign)
        editCallPhone = findViewById(R.id.edit_call_phone)
        editSmsPhone = findViewById(R.id.edit_sms_phone)
        editWebhook = findViewById(R.id.edit_webhook)
        editWebhookSecret = findViewById(R.id.edit_webhook_secret)
        buttonExecute = findViewById(R.id.button_execute)
    }

    private fun initFromPrefs() {
        switchMaster.isChecked = prefs.getBoolean("master", true)
        switchNotify.isChecked = prefs.getBoolean("notify", true)
        switchCall.isChecked = prefs.getBoolean("call", false)
        switchSms.isChecked = prefs.getBoolean("sms", false)
        switchWebhook.isChecked = prefs.getBoolean("webhook", false)
        switchWebhookSign.isChecked = prefs.getBoolean("webhookSign", false)
        editCallPhone.setText(prefs.getString("phoneCall", ""))
        editSmsPhone.setText(prefs.getString("phoneSms", ""))
        editWebhook.setText(prefs.getString("webhookUrl", ""))
        editWebhookSecret.setText(prefs.getString("webhookSecret", ""))
    }

    private fun attachListeners() {
        switchMaster.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("master", isChecked).apply() }
        switchNotify.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("notify", isChecked).apply() }
        switchCall.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("call", isChecked).apply() }
        switchSms.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("sms", isChecked).apply() }
        switchWebhook.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("webhook", isChecked).apply() }
        switchWebhookSign.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("webhookSign", isChecked).apply() }
        editCallPhone.addTextChangedListener(simpleWatcher { prefs.edit().putString("phoneCall", it).apply() })
        editSmsPhone.addTextChangedListener(simpleWatcher { prefs.edit().putString("phoneSms", it).apply() })
        editWebhook.addTextChangedListener(simpleWatcher { prefs.edit().putString("webhookUrl", it).apply() })
        editWebhookSecret.addTextChangedListener(simpleWatcher { prefs.edit().putString("webhookSecret", it).apply() })
        buttonExecute.setOnClickListener { executeIfEnabled(force = true) }
    }

    private fun simpleWatcher(onText: (String) -> Unit) = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onText(s?.toString() ?: "") }
        override fun afterTextChanged(s: android.text.Editable?) {}
    }

    private fun executeIfEnabled(force: Boolean = false) {
        val masterOn = prefs.getBoolean("master", true) || force
        if (!masterOn) return
        if (prefs.getBoolean("notify", true)) maybeRequestNotificationPermissionAndPost()
        if (prefs.getBoolean("call", false)) maybeRequestCallPermissionAndDial()
        if (prefs.getBoolean("sms", false)) maybeRequestSmsPermissionAndSend()
        if (prefs.getBoolean("webhook", false)) sendWebhook()
    }

    private fun maybeRequestNotificationPermissionAndPost() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
                PackageManager.PERMISSION_GRANTED -> postBatteryNotification()
                else -> requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else postBatteryNotification()
    }

    private fun postBatteryNotification() {
        val level = getCurrentBatteryPct()
        LogStore.append(this, "${now()} 通知 电量=$level%")
        (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).apply {
            val channelId = "battery_channel"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = android.app.NotificationChannel(channelId, "电量通知", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                createNotificationChannel(ch)
            }
            val n = androidx.core.app.NotificationCompat.Builder(this@SettingsActivity, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("当前电量")
                .setContentText("电量：$level%")
                .build()
            androidx.core.app.NotificationManagerCompat.from(this@SettingsActivity).notify(2001, n)
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
        LogStore.append(this, "${now()} 拨打电话 $number")
        startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }

    private fun maybeRequestSmsPermissionAndSend() {
        when (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)) {
            PackageManager.PERMISSION_GRANTED -> sendSms()
            else -> requestSmsPermission.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun sendSms() {
        val number = prefs.getString("phoneSms", "")?.trim().orEmpty()
        if (number.isEmpty()) return
        val text = "设备：${Build.MANUFACTURER} ${Build.MODEL}，电量：${getCurrentBatteryPct()}% 时间：${now()}"
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
        sms.sendTextMessage(number, null, text, null, null)
        LogStore.append(this, "${now()} 发送短信 $number 内容=$text")
    }

    private fun sendWebhook() {
        val urlStr = prefs.getString("webhookUrl", "")?.trim().orEmpty()
        if (urlStr.isEmpty()) return
        val body = "{\"msg_type\":\"text\",\"content\":{\"text\":\"设备：${Build.MANUFACTURER} ${Build.MODEL} 电量：${getCurrentBatteryPct()}% 时间：${now()}\"}}"
        Thread {
            try {
                val finalUrl = if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) urlStr else "http://$urlStr"
                val conn = (java.net.URL(finalUrl).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                LogStore.append(this, "${now()} Webhook 响应=$code")
                conn.disconnect()
            } catch (_: Exception) {
                LogStore.append(this, "${now()} Webhook 错误")
            }
        }.start()
    }

    private fun getCurrentBatteryPct(): Int {
        val intent = registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) return (level * 100) / scale
        }
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
}