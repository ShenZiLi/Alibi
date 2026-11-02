package app.myzel394.alibi.ui.utils

import kotlin.math.min
import kotlin.math.roundToInt

/** All values use the same unit (pixels at the window boundary). */
object FloatingPreviewSize {
    fun width(requested: Int, minimum: Int, maximum: Int): Int {
        val upper = maximum.coerceAtLeast(1)
        return requested.coerceIn(min(minimum.coerceAtLeast(1), upper), upper)
    }

    fun height(width: Int, aspectRatio: Float): Int =
        (width / aspectRatio).roundToInt()
}
