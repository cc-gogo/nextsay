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
import java.io.File

class DiagnosticsActivity : Activity() {
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp, 24.dp, 24.dp, 32.dp)
            setBackgroundColor(Color.rgb(244, 246, 245))
        }
        content.addView(TextView(this).apply {
            text = "诊断日志"
            textSize = 26f
            setTextColor(Color.rgb(26, 31, 29))
            fullWidth()
        })
        content.addView(TextView(this).apply {
            text = "日志仅保存在本机，不包含 API Key、对话文字、提示词、草稿、候选回复或网络正文。你可以复制最近一条，或导出全部安全事件。"
            textSize = 14f
            setTextColor(Color.rgb(72, 82, 78))
            setPadding(0, 10.dp, 0, 18.dp)
            fullWidth()
        })
        summary = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(26, 31, 29))
            setPadding(12.dp, 12.dp, 12.dp, 12.dp)
            setBackgroundColor(Color.WHITE)
            fullWidth()
        }
        content.addView(summary)
        content.addView(Button(this).apply {
            text = "复制诊断信息"
            setOnClickListener { copyNewest() }
            fullWidth()
        })
        content.addView(Button(this).apply {
            text = "导出诊断日志"
            setOnClickListener { exportAll() }
            fullWidth()
        })
        content.addView(Button(this).apply {
            text = "清除诊断日志"
            setOnClickListener {
                nextSayDependencies.diagnostics.clear()
                refresh()
                Toast.makeText(this@DiagnosticsActivity, "诊断日志已清除", Toast.LENGTH_SHORT).show()
            }
            fullWidth()
        })
        setContentView(ScrollView(this).apply { addView(content) })
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
        dependencies.diagnosticExportManager.write(output, dependencies.diagnostics.events())
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
