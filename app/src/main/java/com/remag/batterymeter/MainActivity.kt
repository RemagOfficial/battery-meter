package com.remag.batterymeter

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.remag.batterymeter.ui.theme.BatteryMeterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs = remember { getSharedPreferences(BatteryOverlayService.PREFS_NAME, MODE_PRIVATE) }
            var followSystem by remember { 
                mutableStateOf(prefs.getBoolean(BatteryOverlayService.KEY_FOLLOW_SYSTEM, true)) 
            }
            var darkThemePref by remember { 
                mutableStateOf(prefs.getBoolean(BatteryOverlayService.KEY_DARK_MODE, false)) 
            }
            
            val isDark = if (followSystem) isSystemInDarkTheme() else darkThemePref

            BatteryMeterTheme(darkTheme = isDark) {
                var currentScreen by remember { mutableStateOf(Screen.Main) }
                var currentSubMenu by remember { mutableStateOf(SettingsSubMenu.Home) }
                var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled()) }
                var isOverlayEnabled by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
                var isBluetoothPermissionGranted by remember { 
                    mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    } else true)
                }
                var isBatteryOptimized by remember { mutableStateOf(!isIgnoringBatteryOptimizations()) }

                // Refresh status when returning to app
                LaunchedEffect(Unit) {
                    while(true) {
                        isAccessibilityEnabled = isAccessibilityServiceEnabled()
                        isOverlayEnabled = Settings.canDrawOverlays(this@MainActivity)
                        isBluetoothPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                        } else true
                        isBatteryOptimized = !isIgnoringBatteryOptimizations()
                        delay(1000)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (currentScreen == Screen.Settings) {
                            val title = when (currentSubMenu) {
                                SettingsSubMenu.Home -> "Settings"
                                SettingsSubMenu.Appearance -> "Appearance"
                                SettingsSubMenu.ColorRanges -> "Color Ranges"
                                SettingsSubMenu.Customization -> "Customization"
                                SettingsSubMenu.Bluetooth -> "Bluetooth"
                                SettingsSubMenu.Visibility -> "Visibility Restrictions"
                            }
                            SettingsTopBar(title = title) {
                                if (currentSubMenu == SettingsSubMenu.Home) {
                                    currentScreen = Screen.Main
                                } else {
                                    currentSubMenu = SettingsSubMenu.Home
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    if (!isAccessibilityEnabled || !isOverlayEnabled || !isBluetoothPermissionGranted) {
                        PermissionScreen(
                            modifier = Modifier.padding(innerPadding),
                            isAccessibilityEnabled = isAccessibilityEnabled,
                            isOverlayEnabled = isOverlayEnabled,
                            isBluetoothPermissionGranted = isBluetoothPermissionGranted,
                            isBatteryOptimized = isBatteryOptimized,
                            onOpenAccessibility = { openAccessibilitySettings() },
                            onOpenOverlay = { openOverlaySettings() },
                            onOpenBluetoothSettings = { requestBluetoothPermission() },
                            onOpenBatteryOptimizations = { requestIgnoreBatteryOptimizations() }
                        )
                    } else {
                        when (currentScreen) {
                            Screen.Main -> MainScreen(
                                modifier = Modifier.padding(innerPadding),
                                onOpenSettings = { 
                                    currentScreen = Screen.Settings
                                    currentSubMenu = SettingsSubMenu.Home
                                }
                            )
                            Screen.Settings -> {
                                BackHandler { 
                                    if (currentSubMenu == SettingsSubMenu.Home) {
                                        currentScreen = Screen.Main
                                    } else {
                                        currentSubMenu = SettingsSubMenu.Home
                                    }
                                }
                                SettingsScreen(
                                    modifier = Modifier.padding(innerPadding),
                                    currentSubMenu = currentSubMenu,
                                    onSubMenuChange = { currentSubMenu = it },
                                    context = this,
                                    followSystem = followSystem,
                                    onFollowSystemChange = { 
                                        followSystem = it
                                        prefs.edit { putBoolean(BatteryOverlayService.KEY_FOLLOW_SYSTEM, it) }
                                    },
                                    isBatteryOptimized = isBatteryOptimized,
                                    onOpenBatteryOptimizations = { requestIgnoreBatteryOptimizations() },
                                    darkThemePref = darkThemePref,
                                    onDarkThemeChange = { 
                                        darkThemePref = it
                                        prefs.edit { putBoolean(BatteryOverlayService.KEY_DARK_MODE, it) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:$packageName".toUri()
        }
        startActivity(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName &&
                service.resolveInfo.serviceInfo.name == BatteryOverlayService::class.java.name) {
                return true
            }
        }
        return false
    }

    // Bluetooth support
    fun getConnectedBluetoothDevices(context: Context): List<Pair<String, String>> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val bluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return emptyList()
        
        return try {
            adapter.bondedDevices.map { it.address to (it.name ?: "Unknown") }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Find 'Battery Meter' and enable it", Toast.LENGTH_LONG).show()
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        startActivity(intent)
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1001
            )
        }
    }
}

enum class Screen { Main, Settings }
enum class SettingsSubMenu { Home, Appearance, ColorRanges, Customization, Bluetooth, Visibility }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    )
}

@Composable
fun PermissionScreen(
    modifier: Modifier = Modifier,
    isAccessibilityEnabled: Boolean,
    isOverlayEnabled: Boolean,
    isBluetoothPermissionGranted: Boolean,
    isBatteryOptimized: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenBatteryOptimizations: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Permissions Required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "To display the battery meter correctly, please enable the following permissions:",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        PermissionItem(
            title = "Accessibility Service",
            description = "Allows the meter to show over settings and banking apps.",
            isEnabled = isAccessibilityEnabled,
            onClick = onOpenAccessibility
        )
        
        Spacer(Modifier.height(16.dp))

        PermissionItem(
            title = "Draw Over Other Apps",
            description = "Allows the meter to appear on top of other applications.",
            isEnabled = isOverlayEnabled,
            onClick = onOpenOverlay
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Spacer(Modifier.height(16.dp))
            PermissionItem(
                title = "Bluetooth Connection",
                description = "Required to read battery levels of connected devices.",
                isEnabled = isBluetoothPermissionGranted,
                onClick = onOpenBluetoothSettings
            )
        }

        if (isBatteryOptimized) {
            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            
            Text(
                "Optional Optimization",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Prevent the system from killing the app to ensure the meter stays visible.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            
            OutlinedButton(
                onClick = onOpenBatteryOptimizations,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Disable Battery Optimization")
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            if (isEnabled) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Enabled", tint = Color.Green)
            } else {
                Button(onClick = onClick) {
                    Text("Enable")
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(BatteryOverlayService.PREFS_NAME, Context.MODE_PRIVATE) }
    var isEnabled by remember { mutableStateOf(prefs.getBoolean(BatteryOverlayService.KEY_SERVICE_ENABLED, true)) }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Battery Meter", style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Service Active", style = MaterialTheme.typography.titleMedium)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { 
                        isEnabled = it
                        prefs.edit { putBoolean(BatteryOverlayService.KEY_SERVICE_ENABLED, it) }
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Settings")
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    currentSubMenu: SettingsSubMenu,
    onSubMenuChange: (SettingsSubMenu) -> Unit,
    context: Context? = null,
    followSystem: Boolean,
    onFollowSystemChange: (Boolean) -> Unit,
    isBatteryOptimized: Boolean,
    onOpenBatteryOptimizations: () -> Unit,
    darkThemePref: Boolean,
    onDarkThemeChange: (Boolean) -> Unit
) {
    val localContext = LocalContext.current
    val prefs = context?.getSharedPreferences(BatteryOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
    
    val connectedDevices = remember { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(localContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            emptyList()
        } else {
            (localContext.getActivity() as? MainActivity)?.getConnectedBluetoothDevices(localContext) ?: emptyList() 
        }
    }

    var installedApps by remember { mutableStateOf<List<Triple<String, String, Drawable>>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }

    LaunchedEffect(currentSubMenu) {
        if (currentSubMenu == SettingsSubMenu.Visibility) {
            // Clear old list to ensure a fresh load and show progress
            installedApps = emptyList()
            isLoadingApps = true
            val apps = withContext(Dispatchers.IO) {
                val pm = localContext.packageManager
                val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                allApps.filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .map { app ->
                        Triple(app.packageName, app.loadLabel(pm).toString(), app.loadIcon(pm))
                    }
                    .sortedBy { it.second.lowercase() }
            }
            installedApps = apps
            isLoadingApps = false
        }
    }

    var offsetX by remember { mutableFloatStateOf(prefs?.getInt(BatteryOverlayService.KEY_X, 0)?.toFloat() ?: 0f) }
    var offsetY by remember { mutableFloatStateOf(prefs?.getInt(BatteryOverlayService.KEY_Y, 0)?.toFloat() ?: 0f) }
    var meterSize by remember { mutableFloatStateOf(prefs?.getInt(BatteryOverlayService.KEY_SIZE, 40)?.toFloat() ?: 40f) }
    var thickness by remember { mutableFloatStateOf(prefs?.getInt(BatteryOverlayService.KEY_THICKNESS, 4)?.toFloat() ?: 4f) }
    var opacity by remember { mutableFloatStateOf(prefs?.getInt(BatteryOverlayService.KEY_OPACITY, 100)?.toFloat() ?: 100f) }
    var strokeCap by remember { mutableIntStateOf(prefs?.getInt(BatteryOverlayService.KEY_STROKE_CAP, 1) ?: 1) }
    var colorRangesRaw by remember { mutableStateOf(prefs?.getString(BatteryOverlayService.KEY_COLOR_RANGES, BatteryOverlayService.DEFAULT_COLOR_RANGES) ?: BatteryOverlayService.DEFAULT_COLOR_RANGES) }
    var autoReturnToPhone by remember { mutableStateOf(prefs?.getBoolean(BatteryOverlayService.KEY_AUTO_RETURN_TO_PHONE, false) ?: false) }
    var nestedRings by remember { mutableStateOf(prefs?.getBoolean(BatteryOverlayService.KEY_NESTED_RINGS, false) ?: false) }
    var depleteFromRight by remember { mutableStateOf(prefs?.getBoolean(BatteryOverlayService.KEY_DEPLETE_FROM_RIGHT, false) ?: false) }
    var showBg by remember { mutableStateOf(prefs?.getBoolean(BatteryOverlayService.KEY_SHOW_BG, false) ?: false) }
    var chargeSpeed by remember { mutableFloatStateOf(prefs?.getInt(BatteryOverlayService.KEY_CHARGE_SPEED, 50)?.toFloat() ?: 50f) }
    
    var hideOnLockscreen by remember { mutableStateOf(prefs?.getBoolean(BatteryOverlayService.KEY_HIDE_ON_LOCKSCREEN, false) ?: false) }
    var hideNonFullscreen by remember { mutableStateOf(prefs?.getBoolean(BatteryOverlayService.KEY_HIDE_NON_FULLSCREEN, false) ?: false) }
    var blacklistedApps by remember { mutableStateOf(prefs?.getString(BatteryOverlayService.KEY_BLACKLISTED_APPS, "") ?: "") }

    var searchQuery by remember { mutableStateOf("") }

    // Save to prefs whenever values change
    LaunchedEffect(offsetX, offsetY, meterSize, thickness, opacity, strokeCap, colorRangesRaw, autoReturnToPhone, nestedRings, depleteFromRight, showBg, chargeSpeed, hideOnLockscreen, hideNonFullscreen, blacklistedApps) {
        prefs?.edit {
            putInt(BatteryOverlayService.KEY_X, offsetX.toInt())
            putInt(BatteryOverlayService.KEY_Y, offsetY.toInt())
            putInt(BatteryOverlayService.KEY_SIZE, meterSize.toInt())
            putInt(BatteryOverlayService.KEY_THICKNESS, thickness.toInt())
            putInt(BatteryOverlayService.KEY_OPACITY, opacity.toInt())
            putInt(BatteryOverlayService.KEY_STROKE_CAP, strokeCap)
            putString(BatteryOverlayService.KEY_COLOR_RANGES, colorRangesRaw)
            putBoolean(BatteryOverlayService.KEY_AUTO_RETURN_TO_PHONE, autoReturnToPhone)
            putBoolean(BatteryOverlayService.KEY_NESTED_RINGS, nestedRings)
            putBoolean(BatteryOverlayService.KEY_DEPLETE_FROM_RIGHT, depleteFromRight)
            putBoolean(BatteryOverlayService.KEY_SHOW_BG, showBg)
            putInt(BatteryOverlayService.KEY_CHARGE_SPEED, chargeSpeed.toInt())
            putBoolean(BatteryOverlayService.KEY_HIDE_ON_LOCKSCREEN, hideOnLockscreen)
            putBoolean(BatteryOverlayService.KEY_HIDE_NON_FULLSCREEN, hideNonFullscreen)
            putString(BatteryOverlayService.KEY_BLACKLISTED_APPS, blacklistedApps)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (currentSubMenu) {
            SettingsSubMenu.Home -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SettingsNavigationItem(
                        icon = Icons.Default.Palette,
                        title = "Appearance",
                        description = "Theme and optimization settings",
                        onClick = { onSubMenuChange(SettingsSubMenu.Appearance) }
                    )
                    SettingsNavigationItem(
                        icon = Icons.Default.ColorLens,
                        title = "Color Ranges",
                        description = "Battery level color thresholds",
                        onClick = { onSubMenuChange(SettingsSubMenu.ColorRanges) }
                    )
                    SettingsNavigationItem(
                        icon = Icons.Default.Tune,
                        title = "Customization",
                        description = "Position, size, and style",
                        onClick = { onSubMenuChange(SettingsSubMenu.Customization) }
                    )
                    SettingsNavigationItem(
                        icon = Icons.Default.Bluetooth,
                        title = "Bluetooth",
                        description = "Connected device settings",
                        onClick = { onSubMenuChange(SettingsSubMenu.Bluetooth) }
                    )
                    SettingsNavigationItem(
                        icon = Icons.Default.Visibility,
                        title = "Visibility",
                        description = "Restrict where the meter shows",
                        onClick = { onSubMenuChange(SettingsSubMenu.Visibility) }
                    )
                }
            }
            SettingsSubMenu.Appearance -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = "App Theme", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Follow System Theme")
                        Switch(checked = followSystem, onCheckedChange = onFollowSystemChange)
                    }

                    if (!followSystem) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Dark Mode")
                            Switch(checked = darkThemePref, onCheckedChange = onDarkThemeChange)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = "Stability", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (isBatteryOptimized) {
                        OutlinedButton(
                            onClick = onOpenBatteryOptimizations,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Disable Battery Optimization")
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green)
                                Spacer(Modifier.width(8.dp))
                                Text("Battery optimization is disabled")
                            }
                        }
                    }
                }
            }
            SettingsSubMenu.ColorRanges -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = "Thresholds", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Meter color changes based on the first range it falls into (ordered by threshold).", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))

                    ColorRangeEditor(
                        rawRanges = colorRangesRaw,
                        onRangesChanged = { colorRangesRaw = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { colorRangesRaw = BatteryOverlayService.DEFAULT_COLOR_RANGES },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Reset Ranges to Default")
                    }
                }
            }
            SettingsSubMenu.Customization -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = "Overlay Style", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Alignment BG")
                        Switch(checked = showBg, onCheckedChange = { showBg = it })
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Deplete from Right")
                        Switch(checked = depleteFromRight, onCheckedChange = { depleteFromRight = it })
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    StrokeCapSelector(selectedCap = strokeCap, onCapSelected = { strokeCap = it })

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(text = "Position & Size", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AdjustmentSlider(label = "X Offset", value = offsetX, range = -500f..500f) { offsetX = it }
                    AdjustmentSlider(label = "Y Offset", value = offsetY, range = -500f..500f) { offsetY = it }
                    AdjustmentSlider(label = "Inner Diameter", value = meterSize, range = 0f..200f) { meterSize = it }
                    AdjustmentSlider(label = "Ring Thickness", value = thickness, range = 1f..50f) { thickness = it }
                    AdjustmentSlider(label = "Opacity (%)", value = opacity, range = 0f..100f) { opacity = it }
                    AdjustmentSlider(label = "Charge Spin Speed", value = chargeSpeed, range = 0f..100f) { chargeSpeed = it }
                }
            }
            SettingsSubMenu.Bluetooth -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(text = "Bluetooth Behavior", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-return to Phone", style = MaterialTheme.typography.bodyMedium)
                            Text("Automatically switch back after 5 seconds.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = autoReturnToPhone, onCheckedChange = { autoReturnToPhone = it })
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Experimental: Nested Rings Mode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            Text("Show all devices at once as concentric rings.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = nestedRings, onCheckedChange = { nestedRings = it })
                    }

                    if (connectedDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(24.dp))

                        Text(text = "Device Colors", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Tap a device to set its custom ring color.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))

                        connectedDevices.forEach { (address, name) ->
                            var showPicker by remember { mutableStateOf(false) }
                            val deviceKey = BatteryOverlayService.KEY_DEVICE_COLOR_PREFIX + address
                            val colorHex = remember { mutableStateOf(prefs?.getString(deviceKey, "FF00BCD4") ?: "FF0000FF") }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(name, modifier = Modifier.weight(1f))
                                
                                val currentColor = Color(colorHex.value.toLong(16))
                                
                                Box {
                                    Button(
                                        onClick = { showPicker = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = currentColor),
                                        modifier = Modifier.height(32.dp).width(120.dp)
                                    ) {
                                        Text("Pick Color", style = MaterialTheme.typography.labelSmall)
                                    }
                                    
                                    if (showPicker) {
                                        ColorPickerDialog(
                                            initialColor = currentColor,
                                            onColorSelected = { newColor ->
                                                val newHex = newColor.toArgb().toLong().and(0xFFFFFFFFL).toString(16).uppercase()
                                                colorHex.value = newHex
                                                prefs?.edit { putString(deviceKey, newHex) }
                                                showPicker = false
                                            },
                                            onDismiss = { showPicker = false }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            "No connected Bluetooth devices found with battery info.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            SettingsSubMenu.Visibility -> {
                val blacklistedList = remember(blacklistedApps) {
                    blacklistedApps.split(",").filter { it.isNotEmpty() }.toSet()
                }
                
                val filteredApps = remember(installedApps, searchQuery) {
                    if (searchQuery.isBlank()) installedApps
                    else installedApps.filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        Text(text = "Show/Hide Rules", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hide on Lockscreen", style = MaterialTheme.typography.bodyMedium)
                                Text("Prevent showing over AOD and lockscreen.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = hideOnLockscreen, onCheckedChange = { hideOnLockscreen = it })
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hide in Non-Fullscreen", style = MaterialTheme.typography.bodyMedium)
                                Text("Only show when an app is in fullscreen mode.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = hideNonFullscreen, onCheckedChange = { hideNonFullscreen = it })
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(24.dp))

                        Text(text = "App Blacklist", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Select apps to hide the meter.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (blacklistedList.isNotEmpty()) {
                                TextButton(onClick = { blacklistedApps = "" }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Uncheck All (${blacklistedList.size})")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search apps...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (isLoadingApps) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        items(filteredApps, key = { it.first }) { (packageName, label, icon) ->
                            val isChecked = blacklistedList.contains(packageName)
                            
                            ListItem(
                                headlineContent = { Text(label) },
                                supportingContent = { Text(packageName, style = MaterialTheme.typography.labelSmall) },
                                leadingContent = {
                                    Image(
                                        bitmap = icon.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            val newList = if (checked) {
                                                blacklistedList + packageName
                                            } else {
                                                blacklistedList - packageName
                                            }
                                            blacklistedApps = newList.joinToString(",")
                                        }
                                    )
                                },
                                modifier = Modifier.clickable {
                                    val checked = !isChecked
                                    val newList = if (checked) {
                                        blacklistedList + packageName
                                    } else {
                                        blacklistedList - packageName
                                    }
                                    blacklistedApps = newList.joinToString(",")
                                }
                            )
                        }
                        
                        if (filteredApps.isEmpty() && !isLoadingApps) {
                            item {
                                Text(
                                    if (searchQuery.isEmpty()) "No user-installed apps found." else "No apps match \"$searchQuery\"", 
                                    modifier = Modifier.fillMaxWidth().padding(32.dp), 
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsNavigationItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

// Helper to get Activity
fun Context.getActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.getActivity()
    else -> null
}

@Composable
fun ColorRangeEditor(rawRanges: String, onRangesChanged: (String) -> Unit) {
    val ranges = remember { mutableStateListOf<Pair<Int, Long>>() }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(rawRanges) {
        if (!isDragging) {
            val parsed = rawRanges.split(",").mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) {
                    val threshold = parts[0].toInt()
                    val colorHex = parts[1]
                    val colorLong = try {
                        colorHex.toLong(16)
                    } catch (e: Exception) {
                        0xFF00FF00L
                    }
                    threshold to colorLong
                } else null
            }
            ranges.clear()
            ranges.addAll(parsed)
        }
    }

    val updateRaw = { sort: Boolean ->
        val list = if (sort) ranges.sortedByDescending { it.first } else ranges
        onRangesChanged(list.joinToString(",") { 
            "${it.first}:${it.second.toString(16).uppercase()}" 
        })
    }

    val colorOptions = listOf(
        "Green" to 0xFF00FF00L,
        "Red" to 0xFFFF0000L,
        "Yellow" to 0xFFFFFF00L,
        "Blue" to 0xFF0000FFL,
        "Cyan" to 0xFF00FFFFL,
        "Magenta" to 0xFFFF00FFL,
        "White" to 0xFFFFFFFFL,
        "Black" to 0xFF000000L
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        ranges.forEachIndexed { index, range ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Threshold: ${range.first}%", style = MaterialTheme.typography.bodySmall)
                        
                        var showPicker by remember { mutableStateOf(false) }
                        val currentColor = Color(range.second)
                        val currentColorName = colorOptions.find { it.second == range.second }?.first 
                            ?: "#${range.second.toString(16).uppercase()}"
                        
                        Box {
                            Button(
                                onClick = { showPicker = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = currentColor
                                ),
                                modifier = Modifier.height(24.dp).width(100.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    currentColorName, 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (currentColor.toArgb().let { ((it shr 16 and 0xFF) * 0.299 + (it shr 8 and 0xFF) * 0.587 + (it and 0xFF) * 0.114) > 186 }) 
                                        Color.Black else Color.White
                                )
                            }
                            
                            if (showPicker) {
                                ColorPickerDialog(
                                    initialColor = currentColor,
                                    onColorSelected = { newColor ->
                                        ranges[index] = range.first to (newColor.toArgb().toLong() and 0xFFFFFFFFL)
                                        updateRaw(true)
                                        showPicker = false
                                    },
                                    onDismiss = { showPicker = false }
                                )
                            }
                        }
                    }
                    Slider(
                        value = range.first.toFloat(),
                        onValueChange = { 
                            isDragging = true
                            ranges[index] = it.toInt() to range.second
                            updateRaw(false)
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            updateRaw(true)
                        },
                        valueRange = 0f..100f
                    )
                }
                
                if (ranges.size > 1) {
                    IconButton(onClick = {
                        ranges.removeAt(index)
                        updateRaw(true)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }
        }
        
        Button(
            onClick = {
                ranges.add(0 to 0xFF0000FFL)
                updateRaw(true)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Range")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var red by remember { mutableFloatStateOf(initialColor.red) }
    var green by remember { mutableFloatStateOf(initialColor.green) }
    var blue by remember { mutableFloatStateOf(initialColor.blue) }
    var alpha by remember { mutableFloatStateOf(initialColor.alpha) }

    val presetColors = listOf(
        Color.Red, Color.Green, Color.Blue, Color.Yellow, 
        Color.Cyan, Color.Magenta, Color.White, Color.Black
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a Color") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(bottom = 16.dp)
                        .background(Color(red, green, blue, alpha), MaterialTheme.shapes.medium)
                )

                Text("Presets", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(color, CircleShape)
                                .clickable {
                                    red = color.red
                                    green = color.green
                                    blue = color.blue
                                    alpha = color.alpha
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                ColorSlider(label = "Red", value = red, onValueChange = { red = it })
                ColorSlider(label = "Green", value = green, onValueChange = { green = it })
                ColorSlider(label = "Blue", value = blue, onValueChange = { blue = it })
                ColorSlider(label = "Alpha", value = alpha, onValueChange = { alpha = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(Color(red, green, blue, alpha)) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ColorSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text((value * 255).toInt().toString(), style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrokeCapSelector(selectedCap: Int, onCapSelected: (Int) -> Unit) {
    val options = listOf("Square", "Round")
    var expanded by remember { mutableStateOf(false) }
    val safeIndex = if (selectedCap >= options.size) 0 else selectedCap

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text = "Ring Cap Style", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = options[safeIndex],
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEachIndexed { index, selectionOption ->
                    DropdownMenuItem(
                        text = { Text(text = selectionOption) },
                        onClick = {
                            onCapSelected(index)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
fun AdjustmentSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onValueChange((value - 1f).coerceIn(range)) }, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("-")
                }
                Text(text = value.toInt().toString(), modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                Button(onClick = { onValueChange((value + 1f).coerceIn(range)) }, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("+")
                }
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    BatteryMeterTheme {
        SettingsScreen(
            currentSubMenu = SettingsSubMenu.Home,
            onSubMenuChange = {},
            followSystem = true,
            onFollowSystemChange = {},
            isBatteryOptimized = true,
            onOpenBatteryOptimizations = {},
            darkThemePref = false,
            onDarkThemeChange = {}
        )
    }
}
