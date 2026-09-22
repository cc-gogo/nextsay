package app.nextsay.diagnostics

class RotatingDiagnosticRecorder(
    private val storage: DiagnosticStorage,
    private val maxEvents: Int = 200,
    private val maxBytes: Int = 256 * 1024,
    private val maxAgeMillis: Long = SEVEN_DAYS_MILLIS,
) : DiagnosticRecorder {
    private val lock = Any()

    override fun record(event: DiagnosticEvent) {
        runCatching {
            synchronized(lock) {
                val cutoff = event.timestampMillis - maxAgeMillis
                val retained = storage.load()
                    .filter { it.timestampMillis >= cutoff }
                    .plus(event)
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
        synchronized(lock) { storage.load().toList() }
    }.getOrDefault(emptyList())

    override fun find(id: String): DiagnosticEvent? = events().firstOrNull { it.id == id }

    override fun clear() {
        runCatching { synchronized(lock) { storage.save(emptyList()) } }
    }

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
