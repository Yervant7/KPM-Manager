package just.yervant.kpmmanager

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import just.yervant.kpmmanager.ui.CrashHandleActivity
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess
import com.topjohnwu.superuser.Shell

lateinit var kpmmApp: KPMMApplication

const val TAG = "KPM-Manager"

class KPMMApplication : Application(), Thread.UncaughtExceptionHandler {
    lateinit var okhttpClient: OkHttpClient

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    enum class State {
        UNKNOWN_STATE,

        KERNELPATCH_INSTALLED, KERNELPATCH_NEED_UPDATE, KERNELPATCH_NEED_REBOOT, KERNELPATCH_UNINSTALLING
    }


    companion object {
        const val SP_NAME = "config"
        private const val SHOW_BACKUP_WARN = "show_backup_warning"
        lateinit var sharedPreferences: SharedPreferences

        private val _kpStateLiveData = MutableLiveData(State.UNKNOWN_STATE)
        val kpStateLiveData: LiveData<State> = _kpStateLiveData

        var superKey: String = ""
            set(value) {
                field = value
                val ready = Natives.ready(value)
                _kpStateLiveData.value =
                    if (ready) State.KERNELPATCH_INSTALLED else State.UNKNOWN_STATE
                Log.d(TAG, "state: " + _kpStateLiveData.value)
            }
    }

    override fun onCreate() {
        super.onCreate()
        kpmmApp = this
        just.yervant.kpmmanager.services.KPMServiceConnection.bind(this)

        val isArm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        if (!isArm64) {
            Toast.makeText(applicationContext, "Unsupported architecture!", Toast.LENGTH_LONG)
                .show()
            Thread.sleep(5000)
            exitProcess(0)
        }

        Shell.enableVerboseLogging = BuildConfig.DEBUG

        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setContext(this)
                .setTimeout(10)
        )

        // TODO: We can't totally protect superkey from be stolen by root or LSPosed-like injection tools in user space, the only way is don't use superkey,
        // TODO: 1. make me root by kernel
        // TODO: 2. remove all usage of superkey
        sharedPreferences = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        superKey = sharedPreferences.getString("super_key_pref", "su") ?: "su"


        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "KPMManager/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()
    }

    fun getBackupWarningState(): Boolean {
        return sharedPreferences.getBoolean(SHOW_BACKUP_WARN, true)
    }

    fun updateBackupWarningState(state: Boolean) {
        sharedPreferences.edit { putBoolean(SHOW_BACKUP_WARN, state) }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val exceptionMessage = Log.getStackTraceString(e)
        val threadName = t.name
        Log.e(TAG, "Error on thread $threadName:\n $exceptionMessage")
        val intent = Intent(this, CrashHandleActivity::class.java).apply {
            putExtra("exception_message", exceptionMessage)
            putExtra("thread", threadName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        exitProcess(10)
    }
}
