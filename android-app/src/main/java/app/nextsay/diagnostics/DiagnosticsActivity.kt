package app.nextsay.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import app.nextsay.nextSayDependencies
import app.nextsay.ui.NextSayUi
import java.io.File

class DiagnosticsActivity : Activity() {
    private lateinit var summary: TextView
    private val ui by lazy { NextSayUi(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = ui.screen(this, "诊断日志", "出错时，把安全日志分享给我即可排查。", back = { finish() })
        val content = ui.section(root, "最近一次记录")
        content.addView(TextView(this).apply {
            text = "日志仅保存在本机，不包含 API Key、对话文字、提示词、草稿、候选回复或网络正文。你可以复制最近一条，或导出全部安全事件。"
            textSize = 14f
            setTextColor(ui.muted)
            setPadding(0, 10.dp, 0, 18.dp)
            fullWidth()
        })
        summary = TextView(this).apply {
            textSize = 14f
            setTextColor(ui.ink)
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
            background = ui.shape(ui.inset, 12)
            fullWidth()
        }
        content.addView(summary)
        ui.add(content, ui.button("复制诊断信息") { copyNewest() }, 12)
        ui.add(content, ui.button("导出诊断日志", primary = true) { exportAll() }, 10)
        ui.add(content, ui.button("清除诊断日志", destructive = true) {}.apply {
            setOnClickListener {
                val dependencies = nextSayDependencies
                val reportsCleared = dependencies.diagnosticExportManager.clearCachedExports(
                    File(cacheDir, "diagnostics"),
                )
                val eventsCleared = dependencies.diagnostics.clearChecked()
                refresh()
                val message = if (reportsCleared && eventsCleared) {
                    "诊断日志已清除"
                } else {
                    "清除未完成，请重试"
                }
                Toast.makeText(this@DiagnosticsActivity, message, Toast.LENGTH_SHORT).show()
            }
            fullWidth()
        }, 10)
        refresh()
    }

    private fun refresh() {
        val newest = nextSayDependencies.diagnostics.events().lastOrNull()
        summary.text = newest?.let(nextSayDependencies.diagnosticFormatter::compact)
            ?: "暂无诊断记录"
    }

    private fun copyNewest() {
        val newest = nextSayDependencies.diagnostics.events().lastOrNull()
        if (newest == null) {
            Toast.makeText(this, "暂无可复制的诊断记录", Toast.LENGTH_SHORT).show()
            return
        }
        val text = nextSayDependencies.diagnosticFormatter.compact(newest)
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            ClipData.newPlainText("NextSay 诊断信息", text),
        )
        Toast.makeText(this, "诊断信息已复制", Toast.LENGTH_SHORT).show()
    }

    private fun exportAll() {
        val dependencies = nextSayDependencies
        val output = File(
            File(cacheDir, "diagnostics"),
            "nextsay-diagnostics-${System.currentTimeMillis()}.txt",
        )
        val written = runCatching {
            dependencies.diagnosticExportManager.write(output, dependencies.diagnostics.events())
        }.isSuccess
        if (!written) {
            Toast.makeText(this, "导出失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.diagnostics", output)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, "导出 NextSay 诊断日志"))
    }

    private fun android.view.View.fullWidth() {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
