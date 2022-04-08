package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.setting

fun findByText(searchText: String): GrowthType? = GrowthType.values().find { it.text == searchText }

enum class GrowthType(val text: String) {
    TRANSITIVE("Deep Size"),
    RETAINED("Retained Size"),
    DATA_STRUCTURE("Data Structure Size"),
    DEEP_DATA_STRUCTURE("Deep Data Structure Size");
}