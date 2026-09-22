package app.nextsay.diagnostics

interface DiagnosticRecorder {
    fun record(event: DiagnosticEvent)
    fun events(): List<DiagnosticEvent>
    fun find(id: String): DiagnosticEvent?
    fun clear()
}
