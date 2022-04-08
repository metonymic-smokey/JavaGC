
package at.jku.anttracks.gui.utils

import at.jku.anttracks.gui.model.ClientInfo
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.geometry.Bounds
import javafx.scene.control.Dialog
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Window
import java.util.logging.Logger

object WindowUtil {

    private val LOGGER = Logger.getLogger(WindowUtil::class.java.simpleName)

    fun centerOnDefaultScreen(frame: Window) {
        val myScreen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        centerInBounds(frame, myScreen.defaultConfiguration.bounds)
    }

    private fun centerInBounds(frame: Window?, bounds: Rectangle?) {
        if (frame == null || bounds == null) {
            return
        }
        val x = bounds.x + (bounds.width - frame.width) / 2
        val y = bounds.y + (bounds.height - frame.height) / 2
        frame.setLocation(x, y)
        LOGGER.fine("Frame opened on screen (" + bounds.x + ", " + bounds.y + ", " + bounds.width + ", " + bounds.height + ") at (" + x + ", " + y + ")\n" + frame)
    }

    private fun centerInBounds(dialog: Dialog<*>?, bounds: Bounds?) {
        if (dialog == null || bounds == null) {
            throw IllegalArgumentException()
        }

        if (java.lang.Double.isNaN(dialog.height) || java.lang.Double.isNaN(dialog.width)) {
            // cant calculate center position now because properties not calculated yet!
            // trigger calculation once values are present
            val width = dialog.widthProperty()
            val widthListener = object : ChangeListener<Number> {
                override fun changed(widthProperty: ObservableValue<out Number>, oldWidth: Number, newWidth: Number) {
                    if (newWidth.toDouble() != java.lang.Double.NaN) {
                        dialog.x = bounds.minX + (bounds.width - newWidth.toDouble()) / 2
                        width.removeListener(this)     // only trigger ONCE!
                    }
                }
            }
            width.addListener(widthListener)

            val height = dialog.heightProperty()
            val heightListener = object : ChangeListener<Number> {
                override fun changed(heightProperty: ObservableValue<out Number>, oldHeight: Number, newHeight: Number) {
                    if (newHeight.toDouble() != java.lang.Double.NaN) {
                        dialog.y = bounds.minY + (bounds.height - newHeight.toDouble()) / 2
                        height.removeListener(this)     // only trigger ONCE!
                    }
                }
            }
            height.addListener(heightListener)

            LOGGER.fine("Dialog opened on screen (" + bounds.minX + ", " + bounds.minY + ", " + bounds.width + ", " + bounds.height + ") - " +
                                "coordinates to be " +
                                "calculated!")
        } else {
            val x = bounds.minX + (bounds.width - dialog.width) / 2
            val y = bounds.minY + (bounds.height - dialog.height) / 2

            dialog.x = x
            dialog.y = y
            dialog.initOwner(ClientInfo.stage)

            LOGGER.fine("Dialog opened on screen (" + bounds.minX + ", " + bounds.minY + ", " + bounds.width + ", " + bounds.height + ") at (" + x
                                + ", " + y
                                + ")\n" + dialog)
        }
    }

    fun getSizeRelativeToWindowScreen(percent: Int): Dimension {
        val myScreen = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        return Dimension((myScreen.defaultConfiguration.bounds.getWidth() * percent / 100.0).toInt(),
                         (myScreen.defaultConfiguration.bounds.getHeight() * percent / 100.0).toInt())
    }

    fun centerInMainFrame(dialog: Dialog<*>) {
        val root = ClientInfo.mainFrame.root
        WindowUtil.centerInBounds(dialog, root.localToScreen(root.boundsInLocal))
    }
}

