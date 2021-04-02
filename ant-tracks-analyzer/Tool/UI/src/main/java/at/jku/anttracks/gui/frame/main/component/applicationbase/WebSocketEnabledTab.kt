package at.jku.anttracks.gui.frame.main.component.applicationbase

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.annotations.SerializedName

abstract class WebSocketEnabledTab : ApplicationBaseTab() {
    abstract val webSocketHandlers: List<WebSocketCapabilityHandler>

    override fun toJSON(): JsonObject {
        return super.toJSON().apply {
            add("capabilities", JsonArray().apply { webSocketHandlers.map { it.capability }.forEach { add(it.json) } })
        }
    }

    enum class WebSocketCapability(val text: String, val parameters: List<String>) {
        @SerializedName("GET-TAB-LIST")
        GET_TAB_LIST("GET-TAB-LIST", listOf()),

        @SerializedName("GET-TAB-LIST-WITH-CAPABILITY")
        GET_TAB_LIST_WITH_CAPABILITY("GET-TAB-LIST-WITH-CAPABILITY", listOf("capability")),

        @SerializedName("GET-SINGLE-TREE")
        GET_SINGLE_TREE("GET-SINGLE-TREE", listOf()),

        @SerializedName("GET-SINGLE-TREE-POINTER-MAP")
        GET_SINGLE_TREE_POINTER_MAP("GET-SINGLE-TREE-POINTER-MAP", listOf("nodeId")),

        @SerializedName("GET-ALL-TREES")
        GET_ALL_TREES("GET-ALL-TREES", listOf()),

        @SerializedName("GET-ALL-POINTS-TO-MAPS")
        GET_ALL_POINTS_TO_MAPS("GET-ALL-POINTS-TO-MAPS", listOf()),

        @SerializedName("GET-ALL-POINTED-FROM-MAPS")
        GET_ALL_POINTED_FROM_MAPS("GET-ALL-POINTED-FROM-MAPS", listOf());

        val json: JsonObject
            get() = JsonObject().apply {
                add("text", JsonPrimitive(text))
                add("parameters", parameters.fold(JsonArray()) { array, parameter ->
                    array.apply { add(parameter) }
                })
            }

        override fun toString() = text

        companion object {
            fun byText(searchText: String): WebSocketCapability? = WebSocketCapability.values().find { it.text == searchText }
        }
    }

    data class WebSocketCapabilityHandler(val capability: WebSocketEnabledTab.WebSocketCapability, val handler: (List<Any>?) -> JsonElement?) {
        operator fun invoke(parameters: List<Any>?) = handler(parameters)
    }
}
