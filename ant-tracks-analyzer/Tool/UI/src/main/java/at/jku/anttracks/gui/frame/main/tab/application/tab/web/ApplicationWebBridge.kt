package at.jku.anttracks.gui.frame.main.tab.application.tab.web

import at.jku.anttracks.gui.component.antwebview.VueAntWebViewBridge

class ApplicationWebBridge : VueAntWebViewBridge() {

    val x: Int
        get() = getVue("x") as Int

    fun setX(x: Int) {
        setVue("x", x)
    }
}
