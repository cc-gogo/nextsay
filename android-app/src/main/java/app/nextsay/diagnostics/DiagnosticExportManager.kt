package app.nextsay.diagnostics

import java.io.File

class DiagnosticExportManager(
    private val formatter: DiagnosticFormatter,
    private val maxCachedExports: Int = 3,
) {
    fun write(output: File, events: List<DiagnosticEvent>) {
        output.parentFile?.let { directory ->
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create diagnostic export directory" }
        }
        output.writeText(formatter.export(events), Charsets.UTF_8)
        val directory = output.parentFile ?: return
        val exports = ownedExports(directory).sortedByDescending { it.name }
        exports.drop(maxCachedExports).forEach { old ->
            check(old.delete()) { "Cannot remove old diagnostic export" }
        }
    }

    fun clearCachedExports(directory: File): Boolean {
        if (!directory.exists()) return true
        return runCatching {
            ownedExports(directory).fold(true) { allDeleted, file -> file.delete() && allDeleted }
        }.getOrDefault(false)
    }

    private fun ownedExports(directory: File): List<File> =
        directory.listFiles()?.filter {
            it.name.startsWith("nextsay-diagnostics-") && it.name.endsWith(".txt")
        } ?: error("Cannot list diagnostic exports")
}
