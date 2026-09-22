package app.nextsay.diagnostics

import java.io.File

class DiagnosticExportManager(
    private val formatter: DiagnosticFormatter,
) {
    fun write(output: File, events: List<DiagnosticEvent>) {
        output.parentFile?.mkdirs()
        output.writeText(formatter.export(events), Charsets.UTF_8)
    }
}
