package app.nextsay.diagnostics

class RotatingDiagnosticRecorder(
    private val storage: DiagnosticStorage,
    private val maxEvents: Int = 200,
    private val maxBytes: Int = 256 * 1024,
    private val maxAgeMillis: Long = SEVEN_DAYS_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : DiagnosticRecorder {
    private val lock = Any()

    override fun record(event: DiagnosticEvent) {
        runCatching {
            synchronized(lock) {
                val cutoff = nowMillis() - maxAgeMillis
                val retained = storage.load()
                    .filter { it.timestampMillis >= cutoff }
                    .map(DiagnosticEventSanitizer::sanitize)
                    .plus(DiagnosticEventSanitizer.sanitize(event))
                    .takeLast(maxEvents)
                    .toMutableList()
                while (retained.isNotEmpty() && storage.encodedSize(retained) > maxBytes) {
                    retained.removeAt(0)
                }
                storage.save(retained)
            }
        }
    }

    override fun events(): List<DiagnosticEvent> = runCatching {
        synchronized(lock) {
            val stored = storage.load()
            val fresh = stored
                .filter { it.timestampMillis >= nowMillis() - maxAgeMillis }
                .map(DiagnosticEventSanitizer::sanitize)
                .takeLast(maxEvents)
                .toMutableList()
            while (fresh.isNotEmpty() && storage.encodedSize(fresh) > maxBytes) {
                fresh.removeAt(0)
            }
            if (fresh != stored) storage.save(fresh)
            fresh.toList()
        }
    }.getOrDefault(emptyList())

    override fun find(id: String): DiagnosticEvent? = events().firstOrNull { it.id == id }

    override fun clear() {
        clearChecked()
    }

    fun clearChecked(): Boolean = runCatching {
        synchronized(lock) { storage.save(emptyList()) }
        true
    }.getOrDefault(false)

    fun prune(nowMillis: Long) {
        runCatching {
            synchronized(lock) {
                val cutoff = nowMillis - maxAgeMillis
                storage.save(storage.load().filter { it.timestampMillis >= cutoff })
            }
        }
    }

    private companion object {
        const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
