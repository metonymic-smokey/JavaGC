package at.jku.anttracks.gui.component.antwebview

import javafx.scene.web.WebEngine
import netscape.javascript.JSObject

interface AntWebViewBridge {
    val bridgeJS: String

    var webEngine: WebEngine

    fun init(engine: WebEngine) {
        webEngine = engine
    }

    fun injectIntoEngine()

    fun executeScript(command: String): Any? {
        return webEngine.executeScript(command)
    }

    fun setGlobal(member: String, value: Any) = (executeScript("window") as JSObject).setMember(member, value)

    fun redirectConsole() = executeScript("console.log = function(message) { $bridgeJS.log(message); }")
    fun log(s: String) {
        println(s)
    }

    fun logEmpty() {
        log("")
    }
}

open class DefaultAntWebViewBridge : AntWebViewBridge {
    override lateinit var webEngine: WebEngine
    override val bridgeJS: String
        get() = "window.bridge"

    override fun injectIntoEngine() = setGlobal("bridge", this)
}

open class VueAntWebViewBridge : AntWebViewBridge {
    override lateinit var webEngine: WebEngine
    override val bridgeJS: String
        get() = "window.vm.bridge"

    fun setVue(member: String, value: Any) = (executeScript("window.vm") as JSObject).setMember(member, value)
    fun getVue(member: String) = executeScript("window.vm.$member")
    override fun injectIntoEngine() = setVue("bridge", this)
}