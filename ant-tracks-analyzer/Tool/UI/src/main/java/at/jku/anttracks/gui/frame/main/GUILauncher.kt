package at.jku.anttracks.gui.frame.main

import javax.swing.JOptionPane

object GUILauncher {

    @JvmStatic
    fun main(args: Array<String>) {
        try {
            GUI.main(args)
        } catch (t: Throwable) {
            println(t.toString())
            println(t.message)

            var uiText = "An unhandled exception has occured:\n$t\n${t.message}\n${t.cause}\n${t.stackTrace.joinToString("\n")}.\n"
            if(t.message?.contains("javafx/application/Application") == true) {
                uiText += "\n----------\nThis may be due to a missing JavaFX installation." +
                        "\nOn Windows, we suggest to use AntTracks with Oracle Java 8 JDK that comes shipped with JavaFX." +
                        "\nOn Linux, we suggest to use openjdk-8-jdk together with openjfx."

            }
            JOptionPane.showConfirmDialog(null,
                                           uiText,
                                          "Error launching AntTracks",
                                          JOptionPane.OK_OPTION,
                                          JOptionPane.ERROR_MESSAGE)
            System.exit(1)
        }

    }
}
