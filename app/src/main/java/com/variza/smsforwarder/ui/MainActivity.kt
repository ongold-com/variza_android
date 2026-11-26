package com.variza.smsforwarder.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.variza.smsforwarder.ForwarderService
import com.variza.smsforwarder.data.IncomingRecord
import com.variza.smsforwarder.data.MessageStore
import com.variza.smsforwarder.data.Settings
import com.variza.smsforwarder.data.SmsRecord
import com.variza.smsforwarder.net.Sender
import com.variza.smsforwarder.util.PhoneMatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) { App() }
                }
            }
        }
    }
}

/** Shows the login page until a device token is stored, then the main page. */
@Composable
private fun App() {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    var loggedIn by remember { mutableStateOf(settings.isLoggedIn) }
    if (loggedIn) {
        MainScreen(onLogout = { loggedIn = false })
    } else {
        LoginScreen(onLoggedIn = { loggedIn = true })
    }
}

@Composable
private fun LoginScreen(onLoggedIn: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val scope = rememberCoroutineScope()
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Variza SMS", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text("ورود به حساب کاربری", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("شماره موبایل") },
            placeholder = { Text("09123456789") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("رمز عبور") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !loading && phone.isNotBlank() && password.isNotEmpty(),
            onClick = {
                loading = true
                error = ""
                scope.launch {
                    val id = PhoneMatch.digits(phone).ifEmpty { phone.trim() }
                    val r = withContext(Dispatchers.IO) { Sender.login(id, password) }
                    if (r.ok) {
                        settings.saveLogin(r.deviceToken, r.deviceId, r.fullName, r.phone)
                        ForwarderService.start(ctx)
                        onLoggedIn()
                    } else {
                        error = "ورود ناموفق: ${r.message}"
                    }
                    loading = false
                }
            },
        ) { Text(if (loading) "در حال ورود..." else "ورود") }
    }
}

private fun hasSmsPermission(ctx: Context) =
    ctx.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

private fun ignoringBattery(ctx: Context): Boolean {
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(ctx.packageName)
}

@Composable
private fun MainScreen(onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { Settings(ctx) }
    val store = remember { MessageStore.get(ctx) }

    var newNumber by remember { mutableStateOf("") }
    var numbers by remember { mutableStateOf(settings.numbers()) }
    var log by remember { mutableStateOf<List<SmsRecord>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<IncomingRecord>>(emptyList()) }
    var smsOk by remember { mutableStateOf(hasSmsPermission(ctx)) }
    var batteryOk by remember { mutableStateOf(ignoringBattery(ctx)) }
    var confirmLogout by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        smsOk = hasSmsPermission(ctx)
        ForwarderService.start(ctx)
    }

    // Ask for permissions and start the service on open
    LaunchedEffect(Unit) {
        val needed = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        permLauncher.launch(needed.toTypedArray())
    }

    // Refresh the logs and the permission state every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            log = withContext(Dispatchers.IO) { store.recent(50) }
            incoming = withContext(Dispatchers.IO) { store.recentIncoming(100) }
            smsOk = hasSmsPermission(ctx)
            batteryOk = ignoringBattery(ctx)
            delay(2000)
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("خروج از حساب") },
            text = { Text("حساب کاربری، لیست شماره‌ها و همه‌ی لاگ‌های ذخیره‌شده روی گوشی پاک می‌شود و دوباره باید وارد شوی.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    ForwarderService.stop(ctx)
                    settings.clearAll()
                    store.clearAll()
                    ctx.getSystemService(NotificationManager::class.java).cancelAll()
                    onLogout()
                }) { Text("خروج", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("انصراف") } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            // Header with logout button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Variza SMS", style = MaterialTheme.typography.headlineSmall)
                    val who = settings.userName.ifBlank { settings.userPhone }
                    if (who.isNotBlank()) Text(who, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = { confirmLogout = true }) { Text("خروج") }
            }
            Spacer(Modifier.height(8.dp))
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

            Text("شماره‌های پایش‌شده", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
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
                    verticalAlignment = Alignment.CenterVertically,
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
            Text("گزارش همه‌ی پیامک‌های دریافتی", style = MaterialTheme.typography.titleMedium)
            Text(
                "هر پیامکی که به گوشی می‌رسد (از هر فرستنده‌ای) اینجا با نام یا شماره‌ی دقیق فرستنده دیده می‌شود. " +
                    "اگر فرستنده با لیست نخواند، می‌توانی با یک دکمه همان نام دقیق را به لیست اضافه کنی.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (incoming.isEmpty()) Text("هنوز پیامکی دریافت نشده.")
        }

        items(incoming) { r ->
            IncomingRow(r, numbers) {
                if (settings.addNumber(r.sender)) numbers = settings.numbers()
            }
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("وضعیت ارسال به بک‌اند", style = MaterialTheme.typography.titleMedium)
            if (log.isEmpty()) Text("هنوز پیامی ارسال نشده.")
        }

        items(log) { m -> LogRow(m) }

        item { Spacer(Modifier.height(24.dp)) }
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

@Composable
private fun IncomingRow(r: IncomingRecord, numbers: List<String>, onAdd: () -> Unit) {
    val fmt = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US) }
    val matched = r.matchedEntry != null
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("فرستنده: ${r.sender}", style = MaterialTheme.typography.titleSmall)
            Text(r.preview, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                fmt.format(Date(r.receivedAt)) + (if (r.simSlot >= 0) " • سیم ${r.simSlot + 1}" else ""),
                style = MaterialTheme.typography.bodySmall,
            )
            if (matched) {
                Text(
                    "✓ با «${r.matchedEntry}» از لیست مطابقت دارد و ارسال می‌شود",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                val inList = if (numbers.isEmpty()) "لیست خالی است"
                else "در لیست داری: " + numbers.joinToString("، ") { "«$it»" }
                Text(
                    "✗ ارسال نشد: نام/شماره‌ی این فرستنده «${r.sender}» است و با هیچ‌کدام از لیست نمی‌خواند. $inList",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = onAdd) { Text("افزودن «${r.sender}» به لیست") }
            }
        }
    }
}
