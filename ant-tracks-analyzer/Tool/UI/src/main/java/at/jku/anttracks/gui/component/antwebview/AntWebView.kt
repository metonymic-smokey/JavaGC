package at.jku.anttracks.gui.component.antwebview

import at.jku.anttracks.gui.utils.FXMLUtil
import com.sun.javafx.webkit.WebConsoleListener
import javafx.concurrent.Worker
import javafx.fxml.FXML
import javafx.scene.layout.BorderPane
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import org.w3c.dom.Document
import java.net.URL

class AntWebView<T : AntWebViewBridge> : BorderPane() {
    @FXML
    lateinit var wrappedWebView: WebView
    lateinit var engine: WebEngine
    lateinit var bridge: T

    init {
        FXMLUtil.load(this, AntWebView::class.java)
    }

    fun init(path: URL, bridge: T) {
        engine = wrappedWebView.engine
        this.bridge = bridge

        WebConsoleListener.setDefaultListener { webView, message, lineNumber, sourceId -> println("$message[at $lineNumber]") }
        engine.loadWorker.stateProperty().addListener { observable, oldValue, newValue ->
            if (newValue == Worker.State.SUCCEEDED) {
                /*
                https://docs.oracle.com/javase/8/javafx/api/javafx/scene/web/WebEngine.html

                **Mapping JavaScript values to Java objects**
                *
                JavaScript values are represented using the obvious Java classes: null becomes Java null; a boolean becomes a java.lang.Boolean; and a string becomes a java.lang.String. A number can be java.lang.Double or a java.lang.Integer, depending. The undefined value maps to a specific unique String object whose value is "undefined".

                If the result is a JavaScript object, it is wrapped as an instance of the JSObject class. (As a special case, if the JavaScript object is a JavaRuntimeObject as discussed in the next section, then the original Java object is extracted instead.) The JSObject class is a proxy that provides access to methods and properties of its underlying JavaScript object. The most commonly used JSObject methods are getMember (to read a named property), setMember (to set or define a property), and call (to call a function-valued property).

                A DOM Node is mapped to an object that both extends JSObject and implements the appropriate DOM interfaces. To get a JSObject object for a Node just do a cast:

                 JSObject jdoc = (JSObject) webEngine.getDocument();


                In some cases the context provides a specific Java type that guides the conversion. For example if setting a Java String field from a JavaScript expression, then the JavaScript value is converted to a string.
                 */

                // What is the difference between window, screen, and document in Javascript?
                // https://stackoverflow.com/questions/9895202/what-is-the-difference-between-window-screen-and-document-in-javascript
                val window = engine.executeScript("window") as JSObject
                val screen = engine.executeScript("window.screen") as JSObject
                //val document: Document = engine.executeScript("window.document")
                val document: Document = engine.document
                val documentAsJSON = document as JSObject

                // Install bridge between WebView and the application
                /*
                https://docs.oracle.com/javase/8/javafx/api/javafx/scene/web/WebEngine.html

                **Mapping Java objects to JavaScript values**

                The arguments of the JSObject methods setMember and call pass Java objects to the JavaScript environment.
                This is roughly the inverse of the JavaScript-to-Java mapping described above: Java String, Number, or Boolean objects are converted to the obvious JavaScript values.
                A JSObject object is converted to the original wrapped JavaScript object. Otherwise a JavaRuntimeObject is created.
                This is a JavaScript object that acts as a proxy for the Java object, in that accessing properties of the JavaRuntimeObject causes the Java field or method with the same name to be accessed.
                */
                bridge.apply {
                    init(engine)
                    injectIntoEngine()
                    redirectConsole()
                }
            }
        }

        engine.load(path.toExternalForm())
    }
}