package at.jku.anttracks.gui.io.websocket

import at.jku.anttracks.gui.frame.main.component.applicationbase.WebSocketEnabledTab

data class Request(val capability: WebSocketEnabledTab.WebSocketCapability? = null,
                   val tabId: Int = -1,
                   val parameters: List<Any>? = listOf()) {
}