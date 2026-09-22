package app.nextsay.diagnostics

interface DiagnosticStorage {
    fun load(): List<DiagnosticEvent>
    fun save(events: List<DiagnosticEvent>)
    fun encodedSize(events: List<DiagnosticEvent>): Int
}
