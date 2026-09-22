package app.nextsay.overlay

import android.content.Context

class OverlayPositionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): PositionFractions = PositionFractions(
        x = preferences.getFloat(KEY_X_FRACTION, 1f),
        y = preferences.getFloat(KEY_Y_FRACTION, 0.5f),
    )

    fun save(position: PositionFractions) {
        preferences.edit()
            .putFloat(KEY_X_FRACTION, position.x.coerceIn(0f, 1f))
            .putFloat(KEY_Y_FRACTION, position.y.coerceIn(0f, 1f))
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "nextsay_overlay_position"
        const val KEY_X_FRACTION = "x_fraction"
        const val KEY_Y_FRACTION = "y_fraction"
    }
}
