
package at.jku.anttracks.gui.utils

import java.awt.Color
import kotlin.math.roundToInt

object ColorUtil {
    fun perceivedBrightness(c: Color): Int {
        return Math.sqrt(c.red.toDouble() * c.red.toDouble() * .299 + c.green.toDouble() * c.green.toDouble() * .587 + c.blue.toDouble() * c.blue.toDouble() * .114).toInt()
    }

    fun blackOrWhite(on: Color): Color {
        return if (perceivedBrightness(on) > 130) Color.BLACK else Color.WHITE
    }

    fun Color.toFXColor() = javafx.scene.paint.Color.color(red / 255.0, green / 255.0, blue / 255.0, alpha / 255.0)
}

// https://stackoverflow.com/questions/17925318/how-to-get-hex-web-string-from-javafx-colorpicker-color
fun javafx.scene.paint.Color.toRGBAString() = "rgba(${(red * 255.0).roundToInt()}, ${(green * 255.0).roundToInt()}, ${(blue * 255.0).roundToInt()}, $opacity)"

fun javafx.scene.paint.Color.makeTransparent(opacityFactor: Double) = deriveColor(1.0, 1.0, 1.0, opacityFactor)