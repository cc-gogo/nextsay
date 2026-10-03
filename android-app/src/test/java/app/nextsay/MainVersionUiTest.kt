package app.nextsay

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainVersionUiTest {
    private fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
    @Test fun homeShowsInstalledBuildVersionWithoutRequiringAnErrorLog() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val labels = descendants(controller.get().window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue("Version must be visible on the homepage, not only inside logs", labels.any {
                it.contains(BuildConfig.VERSION_NAME) && it.contains("构建 ${BuildConfig.VERSION_CODE}")
            })
        } finally { controller.destroy() }
    }
}
