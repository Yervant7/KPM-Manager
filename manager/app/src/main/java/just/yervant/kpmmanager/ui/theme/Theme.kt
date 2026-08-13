package just.yervant.kpmmanager.ui.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.MutableLiveData
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import just.yervant.kpmmanager.KPMMApplication

enum class ColorMode(val value: Int) {
    SYSTEM(0),
    LIGHT(1),
    DARK(2),
    MONET_SYSTEM(3),
    MONET_LIGHT(4),
    MONET_DARK(5),
    DARK_AMOLED(6);

    companion object {
        fun fromValue(value: Int) = entries.find { it.value == value } ?: SYSTEM
    }

    val isSystem: Boolean get() = value == 0 || value == 3
    val isDark: Boolean get() = value == 2 || value == 5 || value == 6
    val isAmoled: Boolean get() = value == 6
    val isMonet: Boolean get() = value >= 3

    fun toNonMonetMode(): Int = when (this) {
        MONET_SYSTEM -> 0
        MONET_LIGHT -> 1
        MONET_DARK, DARK_AMOLED -> 2
        else -> value
    }

    fun toMonetMode(): Int = when (this) {
        SYSTEM -> 3
        LIGHT -> 4
        DARK -> 5
        else -> value
    }
}

enum class UiMode(val value: String) {
    Miuix("miuix"),
    Material("material");

    companion object {
        fun fromValue(value: String): UiMode = when (value) {
            Material.value -> Material
            else -> Miuix
        }

        val DEFAULT_VALUE = Miuix.value
    }
}

data class AppSettings(
    val colorMode: ColorMode,
    val keyColor: Int,
    val paletteStyle: PaletteStyle,
    val colorSpec: ColorSpec.SpecVersion,
)

fun getSeedColor(customColor: String): Int {
    return when (customColor) {
        "amber" -> 0xFF785900.toInt()
        "blue_grey" -> 0xFF00668A.toInt()
        "blue" -> 0xFF0061A4.toInt()
        "brown" -> 0xFF9A4522.toInt()
        "cyan" -> 0xFF006876.toInt()
        "deep_orange" -> 0xFFB02F00.toInt()
        "deep_purple" -> 0xFF6F43C0.toInt()
        "green" -> 0xFF006E1A.toInt()
        "indigo" -> 0xFF4355B9.toInt()
        "light_blue" -> 0xFF006493.toInt()
        "light_green" -> 0xFF006C48.toInt()
        "lime" -> 0xFF5B6300.toInt()
        "orange" -> 0xFF8B5000.toInt()
        "pink" -> 0xFFBC004B.toInt()
        "purple" -> 0xFF9A25AE.toInt()
        "red" -> 0xFFBB1614.toInt()
        "sakura" -> 0xFF9B404F.toInt()
        "teal" -> 0xFF006A60.toInt()
        "yellow" -> 0xFF695F00.toInt()
        else -> 0xFF0061A4.toInt() // Default to blue
    }
}

fun getAppSettings(prefs: SharedPreferences): AppSettings {
    val darkThemeFollowSys = prefs.getBoolean("night_mode_follow_sys", true)
    val nightModeEnabled = prefs.getBoolean("night_mode_enabled", false)
    val useSystemColorTheme = prefs.getBoolean("use_system_color_theme", true)
    val customColor = prefs.getString("custom_color", "blue") ?: "blue"

    val colorMode = when {
        useSystemColorTheme -> {
            when {
                darkThemeFollowSys -> ColorMode.MONET_SYSTEM
                nightModeEnabled -> ColorMode.MONET_DARK
                else -> ColorMode.MONET_LIGHT
            }
        }
        else -> {
            when {
                darkThemeFollowSys -> ColorMode.SYSTEM
                nightModeEnabled -> ColorMode.DARK
                else -> ColorMode.LIGHT
            }
        }
    }

    val keyColor = if (useSystemColorTheme) 0 else getSeedColor(customColor)

    return AppSettings(
        colorMode = colorMode,
        keyColor = keyColor,
        paletteStyle = PaletteStyle.TonalSpot,
        colorSpec = ColorSpec.SpecVersion.Default
    )
}

@Composable
private fun SystemBarStyle(
    darkMode: Boolean,
    statusBarScrim: Color = Color.Transparent,
    navigationBarScrim: Color = Color.Transparent
) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    SideEffect {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                statusBarScrim.toArgb(),
                statusBarScrim.toArgb(),
            ) { darkMode }, navigationBarStyle = when {
                darkMode -> SystemBarStyle.dark(
                    navigationBarScrim.toArgb()
                )

                else -> SystemBarStyle.light(
                    navigationBarScrim.toArgb(),
                    navigationBarScrim.toArgb(),
                )
            }
        )
    }
}

val refreshTheme = MutableLiveData(false)

@Composable
fun KPMManagerTheme(
    content: @Composable () -> Unit
) {
    val prefs = KPMMApplication.sharedPreferences

    var darkThemeFollowSys by remember {
        mutableStateOf(
            prefs.getBoolean(
                "night_mode_follow_sys",
                true
            )
        )
    }
    var nightModeEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "night_mode_enabled",
                false
            )
        )
    }
    // Dynamic color is available on Android 12+, and custom 1t!
    var dynamicColor by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) prefs.getBoolean(
                "use_system_color_theme",
                true
            ) else false
        )
    }
    var customColorScheme by remember { mutableStateOf(prefs.getString("custom_color", "blue")) }
    var themeStyle by remember { mutableStateOf(prefs.getString("theme_style", "material") ?: "material") }

    val refreshThemeObserver by refreshTheme.observeAsState(false)
    if (refreshThemeObserver == true) {
        darkThemeFollowSys = prefs.getBoolean("night_mode_follow_sys", true)
        nightModeEnabled = prefs.getBoolean("night_mode_enabled", false)
        dynamicColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) prefs.getBoolean(
            "use_system_color_theme",
            true
        ) else false
        customColorScheme = prefs.getString("custom_color", "blue")
        themeStyle = prefs.getString("theme_style", "material") ?: "material"
        refreshTheme.postValue(false)
    }

    val darkTheme = if (darkThemeFollowSys) {
        isSystemInDarkTheme()
    } else {
        nightModeEnabled
    }

    SystemBarStyle(
        darkMode = darkTheme
    )

    val currentAppSettings = getAppSettings(prefs)
    val uiMode = UiMode.fromValue(themeStyle)

    when (uiMode) {
        UiMode.Miuix -> MiuixKPMTheme(
            appSettings = currentAppSettings,
            content = content
        )

        UiMode.Material -> MaterialKPMTheme(
            appSettings = currentAppSettings,
            content = content
        )
    }
}
