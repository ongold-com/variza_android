package com.variza.smsforwarder.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.variza.smsforwarder.ForwarderService
import com.variza.smsforwarder.data.MessageStore
import com.variza.smsforwarder.data.Settings
import com.variza.smsforwarder.data.SmsRecord
import com.variza.smsforwarder.net.Sender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) { MainScreen() }
                }
            }
        }
    }
}

private fun hasSmsPermission(ctx: android.content.Context) =
    ctx.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

private fun ignoringBattery(ctx: android.content.Context): Boolean {
    val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

@Composable
private fun MainScreen() {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val store = remember { MessageStore.get(ctx) }

    var url by remember { mutableStateOf(settings.backendUrl) }
    var newNumber by remember { mutableStateOf("") }
    var numbers by remember { mutableStateOf(settings.numbers()) }
    var info by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf<List<SmsRecord>>(emptyList()) }
    var smsOk by remember { mutableStateOf(hasSmsPermission(ctx)) }
    var batteryOk by remember { mutableStateOf(ignoringBattery(ctx)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        smsOk = hasSmsPermission(ctx)
        ForwarderService.start(ctx)
    }

    // Ask for permissions and start the service on first open
    LaunchedEffect(Unit) {
        val needed = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(needed.toTypedArray())
    }

    // Refresh the log and the permission state every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            log = withContext(Dispatchers.IO) { store.recent(50) }
            smsOk = hasSmsPermission(ctx)
            batteryOk = ignoringBattery(ctx)
            delay(2000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text("Variza SMS", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (smsOk) "✓ مجوز دریافت پیامک فعال است" else "✗ مجوز پیامک داده نشده",
                color = if (smsOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!smsOk) {
                Button(onClick = {
                    permLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS))
                }) { Text("دادن مجوز پیامک") }
            }
            if (!batteryOk) {
                Text("برای کار دائم در بک‌گراند، بهینه‌سازی باتری باید برای این برنامه خاموش باشد.")
                OutlinedButton(onClick = {
                    try {
                        ctx.startActivity(
                            Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:${ctx.packageName}"))
                        )
                    } catch (_: Exception) {
                        ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }
                }) { Text("خاموش کردن بهینه‌سازی باتری") }
            } else {
                Text("✓ بهینه‌سازی باتری خاموش است", color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("آدرس بک‌اند", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("https://example.com/api/sms") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    settings.backendUrl = url
                    info = "آدرس ذخیره شد"
                }) { Text("ذخیره") }
                OutlinedButton(
                    enabled = !testing,
                    onClick = {
                        settings.backendUrl = url
                        testing = true
                        info = "در حال ارسال پیام تست..."
                    },
                ) { Text(if (testing) "..." else "تست اتصال") }
            }
            if (info.isNotEmpty()) Text(info)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("شماره‌های پایش‌شده", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newNumber,
                    onValueChange = { newNumber = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("مثلاً 09121234567 یا نام فرستنده") },
                )
                Button(onClick = {
                    if (settings.addNumber(newNumber)) {
                        numbers = settings.numbers()
                        newNumber = ""
                    }
                }) { Text("افزودن") }
            }
            if (numbers.isEmpty()) Text("هنوز شماره‌ای اضافه نشده؛ تا شماره اضافه نکنی هیچ پیامکی ارسال نمی‌شود.")
        }

        items(numbers) { n ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(n)
                    IconButton(onClick = {
                        settings.removeNumber(n)
                        numbers = settings.numbers()
                    }) { Icon(Icons.Filled.Delete, contentDescription = "حذف") }
                }
            }
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("آخرین پیام‌ها", style = MaterialTheme.typography.titleMedium)
            if (log.isEmpty()) Text("هنوز پیامی دریافت نشده.")
        }

        items(log) { m -> LogRow(m) }

        item { Spacer(Modifier.height(24.dp)) }
    }

    // Run the connection test off the main thread
    LaunchedEffect(testing) {
        if (testing) {
            val json = Sender.buildJson("TEST", "پیام تست از اپ Variza SMS", System.currentTimeMillis(), -1, -1)
            val r = withContext(Dispatchers.IO) { Sender.send(settings.backendUrl, json) }
            info = if (r.ok) "✓ تست موفق بود (${r.message})" else "✗ تست ناموفق: ${r.message}"
            testing = false
        }
    }
}

@Composable
private fun LogRow(m: SmsRecord) {
    val fmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US) }
    val status = when {
        m.status == "SENT" -> "✓ ارسال شد"
        m.attempts == 0 -> "⟳ در صف ارسال"
        else -> "⟳ تلاش مجدد (${m.attempts} بار) – ${m.lastError ?: ""}"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(m.sender, style = MaterialTheme.typography.titleSmall)
            Text(m.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                fmt.format(Date(m.receivedAt)) + (if (m.simSlot >= 0) " • سیم ${m.simSlot + 1}" else ""),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (m.status == "SENT") MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}
