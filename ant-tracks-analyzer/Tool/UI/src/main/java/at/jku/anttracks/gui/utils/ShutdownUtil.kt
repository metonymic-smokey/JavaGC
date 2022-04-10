package at.jku.anttracks.gui.utils

fun addShutdownHook(function: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(object : Thread() {
        override fun run() = function()
    })
}