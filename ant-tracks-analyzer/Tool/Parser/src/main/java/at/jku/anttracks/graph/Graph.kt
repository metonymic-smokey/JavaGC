package at.jku.anttracks.graph

data class Edge(val from: Node, val to: Node, var value: Int = 0) {

    override fun toString(): String {
        return "Edge(from=${from.name}, to=${to.name}, value=$value)"
    }

}

data class Node(val name: String, val edges: MutableMap<String, Edge> = mutableMapOf()) {
    override fun toString(): String {
        return "Node(name='$name', edges=[${edges.keys.joinToString(",")}])"
    }
}

data class Graph(val nodes: MutableMap<String, Node> = mutableMapOf()) {
    fun addEdge(from: String, to: String) {
        nodes.computeIfAbsent(to) { Node(to) }
        nodes.computeIfAbsent(from) { Node(from) }
        nodes[from]!!.edges.computeIfAbsent(to) { Edge(nodes[from]!!, nodes[to]!!) }
        nodes[from]!!.edges[to]!!.value++
    }

    fun toDotString(): String {
        val edges = nodes.values.flatMap { it.edges.values }.joinToString("\n") { "\"${it.from.name}\" -> \"${it.to.name}\"" }
        return "digraph DataStructures {\n$edges\n}"
    }
}