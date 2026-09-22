package app.nextsay.diagnostics

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class JsonFileDiagnosticStorage(
    private val file: File,
    private val gson: Gson = Gson(),
) : DiagnosticStorage {
    private val listType = object : TypeToken<List<DiagnosticEvent>>() {}.type

    override fun load(): List<DiagnosticEvent> {
        if (!file.exists()) return emptyList()
        return gson.fromJson<List<DiagnosticEvent>>(file.readText(Charsets.UTF_8), listType)
            ?: emptyList()
    }

    override fun save(events: List<DiagnosticEvent>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(gson.toJson(events), Charsets.UTF_8)
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    override fun encodedSize(events: List<DiagnosticEvent>): Int =
        gson.toJson(events).toByteArray(Charsets.UTF_8).size
}
