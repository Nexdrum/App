package com.sonolume.spectra

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sonolume.spectra.daw.DawViewModel
import com.sonolume.spectra.ui.DrumScreen
import com.sonolume.spectra.ui.InstrumentDialog
import com.sonolume.spectra.ui.NebScreen
import com.sonolume.spectra.ui.SpecScreen
import com.sonolume.spectra.ui.SpectraBg
import com.sonolume.spectra.ui.SpectraTheme
import com.sonolume.spectra.ui.Transport
import com.sonolume.spectra.ui.collectUi

class MainActivity : ComponentActivity() {
    private val vm: DawViewModel by viewModels()
    private var pendingExport: String? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = res.data?.data ?: return@registerForActivityResult
        try {
            val text = pendingExport ?: "{}"
            contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8).use { it?.write(text) }
            pendingExport = null
            vm.setStatus("Exported JSON.")
        } catch (_: Throwable) {
            vm.setStatus("File operation failed.")
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = res.data?.data ?: return@registerForActivityResult
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8).use { it?.readText() } ?: return@registerForActivityResult
            vm.importText(text)
        } catch (_: Throwable) {
            vm.setStatus("File operation failed.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 9, 13)
        window.navigationBarColor = Color.rgb(8, 9, 13)
        vm.attach(this)
        setContent {
            SpectraTheme {
                Surface(Modifier.fillMaxSize(), color = SpectraBg) {
                    DawApp(
                        vm = vm,
                        onExport = {
                            pendingExport = vm.exportText()
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                                putExtra(Intent.EXTRA_TITLE, "spectradaw.json")
                            }
                            try {
                                exportLauncher.launch(intent)
                            } catch (_: Throwable) {
                                vm.setStatus("File operation failed.")
                            }
                        },
                        onImport = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                            }
                            try {
                                importLauncher.launch(intent)
                            } catch (_: Throwable) {
                                vm.setStatus("File operation failed.")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            NativeAudio.livePanic()
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }
}

@Composable
private fun DawApp(vm: DawViewModel, onExport: () -> Unit, onImport: () -> Unit) {
    val ui = vm.collectUi()
    val bodyScroll = rememberScrollState()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Transport(vm, onExport, onImport)
        Column(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .verticalScroll(bodyScroll)
        ) {
            when (ui.view) {
                1 -> SpecScreen(vm)
                2 -> DrumScreen(vm)
                else -> NebScreen(vm)
            }
        }
    }
    ui.instrument?.let { InstrumentDialog(vm, it) }
}
