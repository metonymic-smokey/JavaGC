package at.jku.anttracks.gui.io.websocket

import at.jku.anttracks.gui.frame.main.component.applicationbase.WebSocketEnabledTab
import at.jku.anttracks.gui.model.ClientInfo
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.jfree.data.json.impl.JSONObject
import java.io.UnsupportedEncodingException
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class AntTracksWebSocketServer(address: InetSocketAddress) : WebSocketServer(address) {

    private val defaultHandlers = listOf(
            WebSocketEnabledTab.WebSocketCapabilityHandler(WebSocketEnabledTab.WebSocketCapability.GET_TAB_LIST) {
                ClientInfo.mainFrame.mainTabbedPane.allTabs.fold(JsonArray()) { array, tab -> array.apply { add(tab.toJSON()) } }
            },
            WebSocketEnabledTab.WebSocketCapabilityHandler(WebSocketEnabledTab.WebSocketCapability.GET_TAB_LIST_WITH_CAPABILITY) { parameters ->
                val searchedCapability = WebSocketEnabledTab.WebSocketCapability.byText(parameters!![0].toString())
                ClientInfo
                        .mainFrame
                        .mainTabbedPane
                        .allTabs
                        .filterIsInstance<WebSocketEnabledTab>()
                        .filter { tab -> tab.webSocketHandlers.find { handler -> handler.capability == searchedCapability } != null }
                        .fold(JsonArray()) { array, tab -> array.apply { add(tab.toJSON()) } }
            }
    )

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        //conn.send("Welcome to the server!") //This method sends a message to the new client
        //broadcast("new connection: " + handshake.resourceDescriptor) //This method sends a message to all clients connected
        println("new connection to " + conn.remoteSocketAddress)
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
        println("closed " + conn.remoteSocketAddress + " with exit code " + code + " additional info: " + reason)
    }

    override fun onMessage(conn: WebSocket, message: String) {
        //val request = JsonParser().parse(message)
        val request = Gson().fromJson<Request>(message, Request::class.java)
        val requestJson = JsonParser().parse(message)
        println("received message from " + conn.remoteSocketAddress + ": " + request)
        val response = JSONObject()
        response["request"] = requestJson
        response["status"] = "failed"
        response["sender"] = -1
        response["data"] = null

        val defaultHandler = defaultHandlers.find { it.capability == request.capability }

        if (request.capability == null) {
            response["error"] = "requested capability was null"
        } else if (defaultHandler != null) {
            response["status"] = "success"
            response["data"] = defaultHandler(request.parameters)
        } else {
            val handler = ClientInfo
                    .mainFrame
                    .mainTabbedPane
                    .allTabs
                    .filterIsInstance<WebSocketEnabledTab>()
                    .find { it.tabId == request.tabId }
                    ?.webSocketHandlers
                    ?.find { it.capability == request.capability }

            if (handler != null) {
                response["status"] = "success"
                response["sender"] = request.tabId
                response["data"] = handler(request.parameters)
            } else {
                response["error"] = "tab with tabId ${request.tabId} not found"
            }
        }
        conn.send(response.toJSONString())
    }

    override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {
        println("received ByteBuffer from " + conn!!.remoteSocketAddress)

        val request: String?
        try {
            request = String(message!!.array(), Charsets.UTF_8)
        } catch (e: UnsupportedEncodingException) {
            conn.send("ENCODING EXCEPTION")
            e.printStackTrace()
            return
        }

        onMessage(conn, request)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        System.err.println("an error occurred on connection ${conn?.remoteSocketAddress ?: "null"}: $ex")
    }

    override fun onStart() {
        println("server started successfully")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val host = "localhost"
            val port = 8887

            val server = AntTracksWebSocketServer(InetSocketAddress(host, port))
            server.run()
        }
    }
}