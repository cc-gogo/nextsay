package app.nextsay

import android.content.Context
import android.os.Build
import app.nextsay.diagnostics.DiagnosticEventFactory
import app.nextsay.diagnostics.DiagnosticExportManager
import app.nextsay.diagnostics.DiagnosticFormatter
import app.nextsay.diagnostics.DiagnosticMetadata
import app.nextsay.diagnostics.JsonFileDiagnosticStorage
import app.nextsay.diagnostics.RotatingDiagnosticRecorder
import app.nextsay.provider.AndroidKeystoreSecretCipher
import app.nextsay.provider.OpenAiCompatibleClient
import app.nextsay.provider.ProviderConfigValidator
import app.nextsay.provider.SharedPreferencesProviderConfigStore
import com.google.gson.Gson
import java.io.File
import okhttp3.OkHttpClient

class AppDependencies(context: Context) {
    private val gson = Gson()

    val providerConfigStore = SharedPreferencesProviderConfigStore(
        context,
        AndroidKeystoreSecretCipher(),
    )
    val providerConfigValidator = ProviderConfigValidator(allowCleartext = BuildConfig.DEBUG)
    val diagnostics = RotatingDiagnosticRecorder(
        JsonFileDiagnosticStorage(File(context.filesDir, "diagnostics/events.json"), gson),
    )
    val diagnosticEventFactory = DiagnosticEventFactory(
        metadataProvider = {
            DiagnosticMetadata(
                appVersion = BuildConfig.VERSION_NAME,
                buildType = BuildConfig.BUILD_TYPE,
                androidVersion = Build.VERSION.RELEASE,
                device = "${Build.MANUFACTURER} ${Build.MODEL}",
            )
        },
    )
    val replyProviderClient = OpenAiCompatibleClient.create(
        okHttpClient = OkHttpClient(),
        gson = gson,
        diagnostics = diagnostics,
        eventFactory = diagnosticEventFactory,
    )
    val diagnosticFormatter = DiagnosticFormatter(gson)
    val diagnosticExportManager = DiagnosticExportManager(diagnosticFormatter)
}
