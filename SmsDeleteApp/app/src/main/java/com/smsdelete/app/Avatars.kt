package com.smsdelete.app

import android.graphics.drawable.GradientDrawable
import kotlin.math.absoluteValue

object Avatars {

    private val PALETTE = intArrayOf(
        0xFF6750A4.toInt(),  // primary purple
        0xFF7D5260.toInt(),  // mauve
        0xFF006C4C.toInt(),  // green
        0xFFB3261E.toInt(),  // red
        0xFF1F6FEB.toInt(),  // blue
        0xFFC07F00.toInt(),  // amber
        0xFF386641.toInt(),  // forest
        0xFF8E5572.toInt(),  // pink
        0xFF005A9C.toInt(),  // navy
        0xFF7A4900.toInt()   // brown
    )

    fun colorFor(key: String): Int {
        if (key.isEmpty()) return PALETTE[0]
        return PALETTE[key.hashCode().absoluteValue % PALETTE.size]
    }

    fun circleDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    fun initial(displayName: String): String {
        val firstLetterOrDigit = displayName.firstOrNull { it.isLetterOrDigit() }
        return firstLetterOrDigit?.uppercase() ?: "#"
    }
}
