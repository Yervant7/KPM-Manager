package just.yervant.kpmmanager.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import just.yervant.kpmmanager.IKPMService
import just.yervant.kpmmanager.KPMMApplication

object KPMServiceConnection : ServiceConnection {
    private const val TAG = "KPMServiceConnection"

    @Volatile
    var service: IKPMService? = null
        private set

    private var binding = false

    fun bind(context: Context) {
        if (service != null || binding) return
        binding = true
        Log.i(TAG, "Binding to KPMService...")
        val intent = Intent(context, KPMService::class.java)
        RootService.bind(intent, this)
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = IKPMService.Stub.asInterface(binder)
        binding = false
        Log.i(TAG, "Root KPMService connected successfully!")

        // Trigger verification / update key state
        val prefs = KPMMApplication.sharedPreferences
        KPMMApplication.superKey = prefs.getString("super_key_pref", "su") ?: "su"

    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
        binding = false
        Log.w(TAG, "Root KPMService disconnected.")
    }
}
