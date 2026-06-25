package just.yervant.kpmmanager.ui.screen

import android.os.Build
import android.system.Os
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.dropUnlessResumed
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AboutScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallModeSelectScreenDestination
import com.ramcosta.composedestinations.generated.destinations.PatchesDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import just.yervant.kpmmanager.KPMMApplication
import just.yervant.kpmmanager.Natives
import just.yervant.kpmmanager.R
import just.yervant.kpmmanager.kpmmApp
import just.yervant.kpmmanager.ui.component.ProvideMenuShape
import just.yervant.kpmmanager.ui.component.WarningCard
import just.yervant.kpmmanager.ui.component.rememberConfirmDialog
import just.yervant.kpmmanager.ui.viewmodel.PatchesViewModel
import just.yervant.kpmmanager.util.LatestVersionInfo
import just.yervant.kpmmanager.util.Version
import just.yervant.kpmmanager.util.Version.getManagerVersion
import just.yervant.kpmmanager.util.checkNewVersion
import just.yervant.kpmmanager.util.getSELinuxStatus
import just.yervant.kpmmanager.util.reboot
import just.yervant.kpmmanager.util.ui.APDialogBlurBehindUtils

private val managerVersion = getManagerVersion()

@Destination<RootGraph>(start = true)
@Composable
fun HomeScreen(navigator: DestinationsNavigator) {
    val kpState by KPMMApplication.kpStateLiveData.observeAsState(KPMMApplication.State.UNKNOWN_STATE)

    Scaffold(topBar = {
        TopBar(onInstallClick = dropUnlessResumed {
            navigator.navigate(InstallModeSelectScreenDestination)
        }, navigator, kpState)
    }) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(0.dp))
            WarningCard()
            KStatusCard(kpState, navigator)
            InfoCard(kpState)
            Spacer(Modifier)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UninstallDialog(showDialog: MutableState<Boolean>, navigator: DestinationsNavigator) {
    BasicAlertDialog(
        onDismissRequest = { showDialog.value = false }, properties = DialogProperties(
            decorFitsSystemWindows = true,
            usePlatformDefaultWidth = false,
        )
    ) {
        Surface(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = AlertDialogDefaults.TonalElevation,
            color = AlertDialogDefaults.containerColor,
        ) {
            Column(modifier = Modifier.padding(PaddingValues(all = 24.dp))) {
                Box(
                    Modifier
                        .padding(PaddingValues(bottom = 16.dp))
                        .align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = stringResource(id = R.string.home_dialog_uninstall_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
            val dialogWindowProvider = LocalView.current.parent as DialogWindowProvider
            APDialogBlurBehindUtils.setupWindowBlurListener(dialogWindowProvider.window)
        }
    }
}

@Composable
fun RebootDropdownItem(@StringRes id: Int, reason: String = "") {
    DropdownMenuItem(text = {
        Text(stringResource(id))
    }, onClick = {
        reboot(reason)
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    onInstallClick: () -> Unit, navigator: DestinationsNavigator, kpState: KPMMApplication.State
) {
    val uriHandler = LocalUriHandler.current
    var showDropdownMoreOptions by remember { mutableStateOf(false) }
    var showDropdownReboot by remember { mutableStateOf(false) }

    TopAppBar(title = {
        Text(stringResource(R.string.app_name))
    }, actions = {
        IconButton(onClick = onInstallClick) {
            Icon(
                imageVector = Icons.Filled.InstallMobile,
                contentDescription = stringResource(id = R.string.mode_select_page_title)
            )
        }

        if (kpState != KPMMApplication.State.UNKNOWN_STATE) {
            IconButton(onClick = {
                showDropdownReboot = true
            }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(id = R.string.reboot)
                )

                ProvideMenuShape(RoundedCornerShape(10.dp)) {
                    DropdownMenu(expanded = showDropdownReboot, onDismissRequest = {
                        showDropdownReboot = false
                    }) {
                        RebootDropdownItem(id = R.string.reboot)
                        RebootDropdownItem(id = R.string.reboot_recovery, reason = "recovery")
                        RebootDropdownItem(id = R.string.reboot_bootloader, reason = "bootloader")
                        RebootDropdownItem(id = R.string.reboot_download, reason = "download")
                        RebootDropdownItem(id = R.string.reboot_edl, reason = "edl")
                    }
                }
            }
        }

        Box {
            IconButton(onClick = { showDropdownMoreOptions = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(id = R.string.settings)
                )
                ProvideMenuShape(RoundedCornerShape(10.dp)) {
                    DropdownMenu(expanded = showDropdownMoreOptions, onDismissRequest = {
                        showDropdownMoreOptions = false
                    }) {
                        DropdownMenuItem(text = {
                            Text(stringResource(R.string.home_more_menu_feedback_or_suggestion))
                        }, onClick = {
                            showDropdownMoreOptions = false
                            uriHandler.openUri("https://github.com/yervant7/KPM-Manager/issues/new/choose")
                        })
                        DropdownMenuItem(text = {
                            Text(stringResource(R.string.home_more_menu_about))
                        }, onClick = {
                            navigator.navigate(AboutScreenDestination)
                            showDropdownMoreOptions = false
                        })
                    }
                }
            }
        }
    })
}

@Composable
private fun KStatusCard(
    kpState: KPMMApplication.State, navigator: DestinationsNavigator
) {

    val showUninstallDialog = remember { mutableStateOf(false) }
    if (showUninstallDialog.value) {
        UninstallDialog(showDialog = showUninstallDialog, navigator)
    }

    val cardBackgroundColor = when (kpState) {
        KPMMApplication.State.KERNELPATCH_INSTALLED -> {
            MaterialTheme.colorScheme.primary
        }

        KPMMApplication.State.KERNELPATCH_NEED_UPDATE, KPMMApplication.State.KERNELPATCH_NEED_REBOOT -> {
            MaterialTheme.colorScheme.secondary
        }

        else -> {
            MaterialTheme.colorScheme.secondaryContainer
        }
    }

    ElevatedCard(
        onClick = {
            if (kpState != KPMMApplication.State.KERNELPATCH_INSTALLED) {
                navigator.navigate(InstallModeSelectScreenDestination)
            }
        },
        colors = CardDefaults.elevatedCardColors(containerColor = cardBackgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (kpState == KPMMApplication.State.UNKNOWN_STATE) 0.dp else 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (kpState == KPMMApplication.State.KERNELPATCH_NEED_UPDATE) {
                Row {
                    Text(
                        text = stringResource(R.string.kernel_patch),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (kpState) {
                    KPMMApplication.State.KERNELPATCH_INSTALLED -> {
                        Icon(Icons.Filled.CheckCircle, stringResource(R.string.home_working))
                    }

                    KPMMApplication.State.KERNELPATCH_NEED_UPDATE, KPMMApplication.State.KERNELPATCH_NEED_REBOOT -> {
                        Icon(Icons.Outlined.SystemUpdate, stringResource(R.string.home_need_update))
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(
                                R.string.kpatch_version_update,
                                Version.installedKPVString(),
                                Version.buildKpmmVString()
                            )
                        )
                    }

                    else -> {
                        Icon(Icons.AutoMirrored.Outlined.HelpOutline, "Unknown")
                    }
                }
                Column(
                    Modifier
                        .weight(2f)
                        .padding(start = 16.dp, end = 1.dp)
                ) {
                    when (kpState) {
                        KPMMApplication.State.KERNELPATCH_INSTALLED -> {
                            Text(
                                text = stringResource(R.string.home_working),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.home_install_unknown),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.home_install_unknown_summary),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (kpState != KPMMApplication.State.UNKNOWN_STATE && kpState != KPMMApplication.State.KERNELPATCH_NEED_UPDATE && kpState != KPMMApplication.State.KERNELPATCH_NEED_REBOOT) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${Version.installedKPVString()} (${managerVersion.second}) - " + "KernelPatch",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Button(onClick = {
                        when (kpState) {
                            KPMMApplication.State.UNKNOWN_STATE -> {
                                navigator.navigate(InstallModeSelectScreenDestination)
                            }

                            KPMMApplication.State.KERNELPATCH_NEED_UPDATE -> {
                                // todo: remove legacy compact for kp < 0.9.0
                                if (Version.installedKPVUInt() < 0x900u) {
                                    navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.PATCH_ONLY))
                                } else {
                                    navigator.navigate(InstallModeSelectScreenDestination)
                                }
                            }

                            KPMMApplication.State.KERNELPATCH_NEED_REBOOT -> {
                                reboot()
                            }

                            KPMMApplication.State.KERNELPATCH_UNINSTALLING -> {
                                // Do nothing
                            }

                            else -> {
                                navigator.navigate(PatchesDestination(PatchesViewModel.PatchMode.UNPATCH))
                            }
                        }
                    }, content = {
                        when (kpState) {
                            KPMMApplication.State.UNKNOWN_STATE -> {
                                Text(text = stringResource(id = R.string.home_ap_cando_install))
                            }

                            KPMMApplication.State.KERNELPATCH_NEED_UPDATE -> {
                                Text(text = stringResource(id = R.string.home_ap_cando_update))
                            }

                            KPMMApplication.State.KERNELPATCH_NEED_REBOOT -> {
                                Text(text = stringResource(id = R.string.home_ap_cando_reboot))
                            }

                            KPMMApplication.State.KERNELPATCH_UNINSTALLING -> {
                                Icon(Icons.Outlined.Cached, contentDescription = "busy")
                            }

                            else -> {
                                Text(text = stringResource(id = R.string.home_ap_cando_uninstall))
                            }
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun WarningCard() {
    var show by rememberSaveable { mutableStateOf(kpmmApp.getBackupWarningState()) }
    if (show) {
        ElevatedCard(
            elevation = CardDefaults.cardElevation(
                defaultElevation = 6.dp
            ), colors = CardDefaults.elevatedCardColors(containerColor = run {
                MaterialTheme.colorScheme.error
            })
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "warning")
                }
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            modifier = Modifier.weight(1f),
                            text = stringResource(id = R.string.patch_warnning),
                        )

                        Spacer(Modifier.width(12.dp))

                        Icon(
                            Icons.Outlined.Clear,
                            contentDescription = "",
                            modifier = Modifier.clickable {
                                show = false
                                kpmmApp.updateBackupWarningState(false)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun getSystemVersion(): String {
    return "${Build.VERSION.RELEASE} ${if (Build.VERSION.PREVIEW_SDK_INT != 0) "Preview" else ""} (API ${Build.VERSION.SDK_INT})"
}

private fun getDeviceInfo(): String {
    var manufacturer =
        Build.MANUFACTURER[0].uppercaseChar().toString() + Build.MANUFACTURER.substring(1)
    if (!Build.BRAND.equals(Build.MANUFACTURER, ignoreCase = true)) {
        manufacturer += " " + Build.BRAND[0].uppercaseChar() + Build.BRAND.substring(1)
    }
    manufacturer += " " + Build.MODEL + " "
    return manufacturer
}

@Composable
private fun InfoCard(kpState: KPMMApplication.State) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
            val contents = StringBuilder()
            val uname = Os.uname()

            @Composable
            fun InfoCardItem(label: String, content: String) {
                contents.appendLine(label).appendLine(content).appendLine()
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
                Text(text = content, style = MaterialTheme.typography.bodyMedium)
            }

            if (kpState != KPMMApplication.State.UNKNOWN_STATE) {
                InfoCardItem(
                    stringResource(R.string.home_kpatch_version), Version.installedKPVString()
                )

             }

            InfoCardItem(stringResource(R.string.home_device_info), getDeviceInfo())

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_kernel), uname.release)

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_system_version), getSystemVersion())

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_fingerprint), Build.FINGERPRINT)

            Spacer(Modifier.height(16.dp))
            InfoCardItem(stringResource(R.string.home_selinux_status), getSELinuxStatus())

        }
    }
}
