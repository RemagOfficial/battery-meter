package com.remag.batterymeter

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay

class BatteryOverlayService : AccessibilityService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        const val PREFS_NAME = "BatteryMeterPrefs"
        const val KEY_X = "offset_x"
        const val KEY_Y = "offset_y"
        const val KEY_SIZE = "meter_size"
        const val KEY_SHOW_BG = "show_bg"
        const val KEY_CHARGE_SPEED = "charge_speed"
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_FOLLOW_SYSTEM = "follow_system"
        const val KEY_THICKNESS = "meter_thickness"
        const val KEY_OPACITY = "meter_opacity"
        const val KEY_STROKE_CAP = "meter_stroke_cap"
        const val KEY_COLOR_RANGES = "meter_color_ranges"
        const val KEY_DEPLETE_FROM_RIGHT = "deplete_from_right"
        const val KEY_DEVICE_COLOR_PREFIX = "device_color_"
        const val KEY_AUTO_RETURN_TO_PHONE = "auto_return_to_phone"
        const val KEY_NESTED_RINGS = "nested_rings"
        const val KEY_HIDE_ON_LOCKSCREEN = "hide_on_lockscreen"
        const val KEY_HIDE_NON_FULLSCREEN = "hide_non_fullscreen"
        const val KEY_BLACKLISTED_APPS = "blacklisted_apps"
        
        const val DEFAULT_COLOR_RANGES = "100:FF00FF00,50:FFFFFF00,20:FFFF0000" // Green, Yellow, Red
    }

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var toastView: ComposeView? = null
    
    // Battery Sources
    private val sources = mutableStateListOf<BatterySource>()
    private var currentSourceIndex by mutableIntStateOf(0)
    
    private var offsetX by mutableIntStateOf(0)
    private var offsetY by mutableIntStateOf(0)
    private var meterSize by mutableIntStateOf(40)
    private var meterThickness by mutableIntStateOf(4)
    private var meterOpacity by mutableIntStateOf(100)
    private var meterStrokeCap by mutableIntStateOf(1) // Default to Round
    private var meterColorRanges by mutableStateOf(DEFAULT_COLOR_RANGES)
    private var depleteFromRight by mutableStateOf(false)
    private var isMeterVisible by mutableStateOf(true)
    private var showBg by mutableStateOf(value = false)
    private var chargeSpeed by mutableIntStateOf(50)
    private var currentRotation by mutableIntStateOf(Surface.ROTATION_0)
    private var autoReturnToPhone by mutableStateOf(false)
    private var nestedRings by mutableStateOf(false)
    private var hideOnLockscreen by mutableStateOf(false)
    private var hideNonFullscreen by mutableStateOf(false)
    private var blacklistedApps by mutableStateOf("")
    
    // Visibility restriction state
    private var isAppBlacklisted by mutableStateOf(false)
    private var isNonFullscreenHidden by mutableStateOf(false)
    private var isLockscreenHidden by mutableStateOf(false)
    
    // Toast state
    private var toastMessage by mutableStateOf("")
    private var showToast by mutableStateOf(false)
    private var toastTrigger by mutableIntStateOf(0) // Used to trigger toast even if index doesn't change

    private val handler = Handler(Looper.getMainLooper())
    private var rotationCheckCount = 0
    private val rotationCheckRunnable = object : Runnable {
        override fun run() {
            updateOverlayParams()
            if (rotationCheckCount < 5) {
                rotationCheckCount++
                handler.postDelayed(this, 200)
            }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            rotationCheckCount = 0
            handler.removeCallbacks(rotationCheckRunnable)
            handler.post(rotationCheckRunnable)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private lateinit var prefs: SharedPreferences
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            KEY_X -> offsetX = p.getInt(KEY_X, 0)
            KEY_Y -> offsetY = p.getInt(KEY_Y, 0)
            KEY_SIZE -> meterSize = p.getInt(KEY_SIZE, 40)
            KEY_THICKNESS -> meterThickness = p.getInt(KEY_THICKNESS, 4)
            KEY_OPACITY -> meterOpacity = p.getInt(KEY_OPACITY, 100)
            KEY_STROKE_CAP -> meterStrokeCap = p.getInt(KEY_STROKE_CAP, 1)
            KEY_COLOR_RANGES -> meterColorRanges = p.getString(KEY_COLOR_RANGES, DEFAULT_COLOR_RANGES) ?: DEFAULT_COLOR_RANGES
            KEY_DEPLETE_FROM_RIGHT -> depleteFromRight = p.getBoolean(KEY_DEPLETE_FROM_RIGHT, false)
            KEY_SERVICE_ENABLED -> {
                isMeterVisible = p.getBoolean(KEY_SERVICE_ENABLED, true)
                updateOverlayVisibility()
            }
            KEY_SHOW_BG -> showBg = p.getBoolean(KEY_SHOW_BG, false)
            KEY_CHARGE_SPEED -> chargeSpeed = p.getInt(KEY_CHARGE_SPEED, 50)
            KEY_AUTO_RETURN_TO_PHONE -> autoReturnToPhone = p.getBoolean(KEY_AUTO_RETURN_TO_PHONE, false)
            KEY_NESTED_RINGS -> nestedRings = p.getBoolean(KEY_NESTED_RINGS, false)
            KEY_HIDE_ON_LOCKSCREEN -> {
                hideOnLockscreen = p.getBoolean(KEY_HIDE_ON_LOCKSCREEN, false)
                checkVisibilityRestrictions()
            }
            KEY_HIDE_NON_FULLSCREEN -> {
                hideNonFullscreen = p.getBoolean(KEY_HIDE_NON_FULLSCREEN, false)
                checkVisibilityRestrictions()
            }
            KEY_BLACKLISTED_APPS -> {
                blacklistedApps = p.getString(KEY_BLACKLISTED_APPS, "") ?: ""
                checkVisibilityRestrictions()
            }
        }
        updateOverlayParams()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryLevel = if (level != -1 && scale != -1) level.toFloat() / scale.toFloat() else 0f
                    
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                    
                    updatePhoneSource(batteryLevel, isCharging)
                }
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    if (device != null && level != -1) {
                        updateBluetoothSource(device, level.toFloat() / 100f)
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        removeBluetoothSource(device)
                    }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT -> {
                    checkVisibilityRestrictions()
                }
            }
        }
    }

    private fun updatePhoneSource(level: Float, isCharging: Boolean) {
        val existing = sources.indexOfFirst { it.id == "phone" }
        val newSource = BatterySource("phone", "Phone", level, isCharging)
        if (existing != -1) {
            sources[existing] = newSource
        } else {
            sources.add(0, newSource)
        }
    }

    private fun updateBluetoothSource(device: BluetoothDevice, level: Float) {
        val address = device.address
        val name = try { device.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
        val existing = sources.indexOfFirst { it.id == address }
        val newSource = BatterySource(address, name, level, isCharging = false, isBluetooth = true)
        
        if (existing != -1) {
            sources[existing] = newSource
        } else {
            sources.add(newSource)
        }
    }

    private fun removeBluetoothSource(device: BluetoothDevice) {
        val index = sources.indexOfFirst { it.id == device.address }
        if (index != -1) {
            sources.removeAt(index)
            if (currentSourceIndex >= sources.size) {
                currentSourceIndex = 0
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize these here as well for early use if needed
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        offsetX = prefs.getInt(KEY_X, 0)
        offsetY = prefs.getInt(KEY_Y, 0)
        meterSize = prefs.getInt(KEY_SIZE, 40)
        meterThickness = prefs.getInt(KEY_THICKNESS, 4)
        meterOpacity = prefs.getInt(KEY_OPACITY, 100)
        meterStrokeCap = prefs.getInt(KEY_STROKE_CAP, 1)
        meterColorRanges = prefs.getString(KEY_COLOR_RANGES, DEFAULT_COLOR_RANGES) ?: DEFAULT_COLOR_RANGES
        depleteFromRight = prefs.getBoolean(KEY_DEPLETE_FROM_RIGHT, false)
        isMeterVisible = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        showBg = prefs.getBoolean(KEY_SHOW_BG, false)
        chargeSpeed = prefs.getInt(KEY_CHARGE_SPEED, 50)
        autoReturnToPhone = prefs.getBoolean(KEY_AUTO_RETURN_TO_PHONE, false)
        nestedRings = prefs.getBoolean(KEY_NESTED_RINGS, false)
        hideOnLockscreen = prefs.getBoolean(KEY_HIDE_ON_LOCKSCREEN, false)
        hideNonFullscreen = prefs.getBoolean(KEY_HIDE_NON_FULLSCREEN, false)
        blacklistedApps = prefs.getString(KEY_BLACKLISTED_APPS, "") ?: ""
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Primary Ring View
        val ringParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            windowAnimations = 0 // Disable all system animations
            x = offsetX
            y = offsetY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BatteryOverlayService)
            setViewTreeViewModelStoreOwner(this@BatteryOverlayService)
            setViewTreeSavedStateRegistryOwner(this@BatteryOverlayService)
            setContent {
                val cap = when (meterStrokeCap) {
                    0 -> StrokeCap.Butt
                    else -> StrokeCap.Round
                }
                val ranges = parseColorRanges(meterColorRanges)
                
                if (sources.isNotEmpty()) {
                    if (nestedRings) {
                        Box(contentAlignment = Alignment.Center) {
                            sources.forEachIndexed { index, source ->
                                val deviceColorHex = if (source.isBluetooth) {
                                    prefs.getString(KEY_DEVICE_COLOR_PREFIX + source.id, null)
                                } else null
                                val bluetoothColor = deviceColorHex?.let { Color(it.toLong(16)) }

                                BatteryMeter(
                                    level = source.level,
                                    sizeDp = meterSize + (index * meterThickness),
                                    thicknessDp = meterThickness,
                                    opacity = meterOpacity,
                                    cap = cap,
                                    colorRanges = ranges,
                                    depleteFromRight = depleteFromRight,
                                    showBg = showBg && index == 0,
                                    isCharging = source.isCharging,
                                    chargeSpeed = chargeSpeed,
                                    bluetoothColor = bluetoothColor
                                )
                            }
                        }
                    } else {
                        val currentSource = sources.getOrNull(currentSourceIndex) ?: sources[0]
                        val deviceColorHex = if (currentSource.isBluetooth) {
                            prefs.getString(KEY_DEVICE_COLOR_PREFIX + currentSource.id, null)
                        } else null
                        
                        val bluetoothColor = deviceColorHex?.let { Color(it.toLong(16)) }

                        LaunchedEffect(currentSourceIndex, toastTrigger) {
                            toastMessage = "${currentSource.name}: ${(currentSource.level * 100).toInt()}%"
                            showToast = true
                            
                            if (autoReturnToPhone && currentSource.isBluetooth) {
                                delay(5000)
                                if (currentSourceIndex == sources.indexOf(currentSource)) {
                                    currentSourceIndex = sources.indexOfFirst { it.id == "phone" }.coerceAtLeast(0)
                                    toastTrigger++
                                }
                            } else {
                                delay(2000)
                            }
                            showToast = false
                        }

                        RingContent(
                            source = currentSource,
                            size = meterSize,
                            thickness = meterThickness,
                            opacity = meterOpacity,
                            cap = cap,
                            ranges = ranges,
                            depleteFromRight = depleteFromRight,
                            showBg = showBg,
                            chargeSpeed = chargeSpeed,
                            bluetoothColor = bluetoothColor,
                            onClick = { 
                                currentSourceIndex = (currentSourceIndex + 1) % sources.size
                                toastTrigger++ 
                            },
                            onLongClick = { 
                                currentSourceIndex = sources.indexOfFirst { it.id == "phone" }.coerceAtLeast(0)
                                toastTrigger++
                            }
                        )
                    }
                }
            }
        }

        // 2. Separate Toast View
        // We use a fixed height and width for the internal Box to prevent window resizing
        // which is what causes the "sliding" animation.
        val toastBoxWidth = 300
        val toastBoxHeight = 150
        val density = resources.displayMetrics.density

        val toastParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            windowAnimations = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        toastView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@BatteryOverlayService)
            setViewTreeViewModelStoreOwner(this@BatteryOverlayService)
            setViewTreeSavedStateRegistryOwner(this@BatteryOverlayService)
            setContent {
                Box(
                    modifier = Modifier.size(width = toastBoxWidth.dp, height = toastBoxHeight.dp),
                    contentAlignment = when (currentRotation) {
                        Surface.ROTATION_0 -> Alignment.TopCenter
                        Surface.ROTATION_90 -> Alignment.CenterStart
                        Surface.ROTATION_180 -> Alignment.BottomCenter
                        Surface.ROTATION_270 -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                ) {
                    AnimatedVisibility(
                        visible = showToast,
                        enter = fadeIn(animationSpec = tween(300)),
                        exit = fadeOut(animationSpec = tween(300))
                    ) {
                        ToastPill(message = toastMessage, opacity = meterOpacity)
                    }
                }
            }
        }

        windowManager.addView(composeView, ringParams)
        windowManager.addView(toastView, toastParams)
        updateOverlayParams()
        updateOverlayVisibility()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(batteryReceiver, filter)
        
        // Initial check for phone battery
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val batteryLevel = if (level != -1 && scale != -1) level.toFloat() / scale.toFloat() else 0f
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            updatePhoneSource(batteryLevel, isCharging)
        }

        // Initial check for connected Bluetooth devices
        try {
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = bluetoothManager.adapter
            if (adapter != null) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || 
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    adapter.bondedDevices.forEach { device ->
                        // Using reflection to get battery level as it might not be public on all SDKs
                        try {
                            val method = device.javaClass.getMethod("getBatteryLevel")
                            val level = method.invoke(device) as Int
                            if (level != -1) {
                                updateBluetoothSource(device, level.toFloat() / 100f)
                            }
                        } catch (e: Exception) {
                            // Fallback to -1 if reflection fails
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore if bluetooth not available
        }
        
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    private fun updateOverlayParams() {
        val ringView = composeView ?: return
        val toastView = toastView ?: return
        if (ringView.parent == null || toastView.parent == null) return
        
        val ringParams = ringView.layoutParams as WindowManager.LayoutParams
        val toastParams = toastView.layoutParams as WindowManager.LayoutParams
        
        val rotation = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.rotation ?: windowManager.defaultDisplay.rotation
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        
        currentRotation = rotation

        val density = resources.displayMetrics.density
        val ringBaseSizeDp = (meterSize + meterThickness)
        val ringCount = if (nestedRings) sources.size.coerceAtLeast(1) else 1
        val extraSizeDp = (ringCount - 1) * meterThickness
        val ringTotalSizePx = ((ringBaseSizeDp + extraSizeDp) * density).toInt()
        val gapPx = (20 * density).toInt()

        // Sync Toast Window Gravity and Position with Ring
        // This ensures they stay together without complex coordinate math.
        when (rotation) {
            Surface.ROTATION_0 -> {
                ringParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                ringParams.x = offsetX
                ringParams.y = offsetY
                
                toastParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                toastParams.x = offsetX
                toastParams.y = offsetY + ringTotalSizePx + gapPx
            }
            Surface.ROTATION_90 -> {
                ringParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                ringParams.x = offsetY
                ringParams.y = -offsetX
                
                toastParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                toastParams.x = offsetY + ringTotalSizePx + gapPx
                toastParams.y = -offsetX
            }
            Surface.ROTATION_180 -> {
                ringParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ringParams.x = -offsetX
                ringParams.y = offsetY
                
                toastParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                toastParams.x = -offsetX
                toastParams.y = offsetY + ringTotalSizePx + gapPx
            }
            Surface.ROTATION_270 -> {
                ringParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                ringParams.x = offsetY
                ringParams.y = offsetX
                
                toastParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                toastParams.x = offsetY + ringTotalSizePx + gapPx
                toastParams.y = offsetX
            }
        }
        
        try {
            windowManager.updateViewLayout(ringView, ringParams)
            windowManager.updateViewLayout(toastView, toastParams)
        } catch (e: Exception) {
            // View might not be attached yet
        }
    }

    private fun updateOverlayVisibility() {
        val visible = isMeterVisible && !isAppBlacklisted && !isNonFullscreenHidden && !isLockscreenHidden
        composeView?.visibility = if (visible) View.VISIBLE else View.GONE
        toastView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rotationCheckCount = 0
        handler.removeCallbacks(rotationCheckRunnable)
        handler.post(rotationCheckRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        
        handler.removeCallbacks(rotationCheckRunnable)
        
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayManager.unregisterDisplayListener(displayListener)
        
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        composeView?.let { windowManager.removeView(it) }
        toastView?.let { windowManager.removeView(it) }
        unregisterReceiver(batteryReceiver)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, 
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                checkVisibilityRestrictions()
            }
        }
    }

    private fun checkVisibilityRestrictions() {
        checkLockscreen()
        
        val windowList = windows
        
        // 1. Find the active application package for the blacklist
        val topAppWindow = windowList.find { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        if (topAppWindow != null) {
            val root = topAppWindow.root
            val packageName = root?.packageName?.toString() ?: ""
            isAppBlacklisted = hideBlacklistedApp(packageName)
        } else {
            isAppBlacklisted = false
        }

        // 2. Check Fullscreen logic
        // We detect "Non-Fullscreen" by checking if the Status Bar (System Window at the top) 
        // is visible and has height.
        if (hideNonFullscreen) {
            var isStatusBarVisible = false
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            
            for (window in windowList) {
                // Status Bar is usually TYPE_SYSTEM or a specialized system type
                if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM) {
                    val bounds = Rect()
                    window.getBoundsInScreen(bounds)
                    
                    // A status bar is at the top (y=0), spans the width, and has a typical height
                    val isAtTop = bounds.top == 0
                    val isFullWidth = bounds.width() >= screenWidth * 0.9
                    val hasNormalHeight = bounds.height() in 10..300 
                    
                    if (isAtTop && isFullWidth && hasNormalHeight) {
                        isStatusBarVisible = true
                        break
                    }
                }
            }
            
            isNonFullscreenHidden = isStatusBarVisible
        } else {
            isNonFullscreenHidden = false
        }
        
        updateOverlayVisibility()
    }

    private fun checkLockscreen() {
        if (hideOnLockscreen) {
            val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            isLockscreenHidden = km.isKeyguardLocked
        } else {
            isLockscreenHidden = false
        }
    }

    private fun hideBlacklistedApp(currentPackage: String): Boolean {
        if (blacklistedApps.isEmpty() || currentPackage.isEmpty()) return false
        val list = blacklistedApps.split(",").map { it.trim() }
        return list.contains(currentPackage)
    }

    override fun onInterrupt() {}

    private fun parseColorRanges(raw: String): List<Pair<Int, Color>> {
        return try {
            raw.split(",").map {
                val parts = it.split(":")
                val threshold = parts[0].toInt()
                val colorHex = parts[1]
                val colorLong = colorHex.toLong(16)
                threshold to Color(colorLong)
            }.sortedBy { it.first }
        } catch (e: Exception) {
            listOf(100 to Color.Green, 50 to Color.Yellow, 20 to Color.Red)
        }
    }
}

data class BatterySource(
    val id: String,
    val name: String,
    val level: Float,
    val isCharging: Boolean = false,
    val isBluetooth: Boolean = false
)

@Composable
fun RingContent(
    source: BatterySource,
    size: Int,
    thickness: Int,
    opacity: Int,
    cap: StrokeCap,
    ranges: List<Pair<Int, Color>>,
    depleteFromRight: Boolean,
    showBg: Boolean,
    chargeSpeed: Int,
    bluetoothColor: Color?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(modifier = Modifier.pointerInput(Unit) {
        detectTapGestures(
            onTap = { onClick() },
            onLongPress = { onLongClick() }
        )
    }) {
        BatteryMeter(
            level = source.level,
            sizeDp = size,
            thicknessDp = thickness,
            opacity = opacity,
            cap = cap,
            colorRanges = ranges,
            depleteFromRight = depleteFromRight,
            showBg = showBg,
            isCharging = source.isCharging,
            chargeSpeed = chargeSpeed,
            bluetoothColor = bluetoothColor
        )
    }
}

@Composable
fun ToastPill(message: String, opacity: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                Color.Black.copy(alpha = 0.7f * (opacity / 100f)),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun BatteryMeter(
    level: Float, 
    sizeDp: Int, 
    thicknessDp: Int, 
    opacity: Int, 
    cap: StrokeCap, 
    colorRanges: List<Pair<Int, Color>>, 
    depleteFromRight: Boolean, 
    showBg: Boolean, 
    isCharging: Boolean, 
    chargeSpeed: Int,
    bluetoothColor: Color? = null
) {
    // totalSize is inner diameter + 2 * half-stroke
    val strokeWidthDp = thicknessDp.dp
    val totalSize = sizeDp.dp + strokeWidthDp
    
    val rotation = remember { Animatable(0f) }
    
    LaunchedEffect(isCharging, chargeSpeed, level, bluetoothColor) {
        if (isCharging && chargeSpeed > 0 && level < 1f && bluetoothColor == null) {
            // Map 1-100 to 5000ms - 200ms duration
            val duration = (101 - chargeSpeed) * 48 + 200
            while (true) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(duration, easing = LinearEasing)
                )
            }
        } else {
            rotation.snapTo(0f)
        }
    }

    Canvas(modifier = Modifier.size(totalSize)) {
        val strokeWidthPx = strokeWidthDp.toPx()
        val drawSize = sizeDp.dp.toPx()
        val offset = strokeWidthPx / 2f
        
        val arcSize = Size(drawSize, drawSize)
        val arcTopLeft = Offset(offset, offset)
        
        val alpha = opacity / 100f

        val meterColor = bluetoothColor ?: colorRanges.firstOrNull { it.first >= (level * 100).toInt() }?.second 
            ?: colorRanges.lastOrNull()?.second 
            ?: Color.Green

        if (showBg) {
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = (drawSize / 2f) + (strokeWidthPx / 2f) + 5,
                center = Offset(totalSize.toPx() / 2f, totalSize.toPx() / 2f)
            )
        }

        val baseAngle = if (isCharging && chargeSpeed > 0 && level < 1f && bluetoothColor == null) {
            if (depleteFromRight) -rotation.value - 90f else rotation.value - 90f
        } else {
            -90f
        }

        drawArc(
            color = meterColor.copy(alpha = alpha),
            startAngle = baseAngle,
            sweepAngle = if (depleteFromRight) -360f * level else 360f * level,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx, cap = cap)
        )
    }
}
