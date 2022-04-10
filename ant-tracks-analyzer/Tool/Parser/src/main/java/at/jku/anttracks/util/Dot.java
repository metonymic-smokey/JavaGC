package at.jku.anttracks.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Dot {

    public static class DotNode {
        String key;
        String[] formats;

        public DotNode(String key, String[] formats) {
            this.key = key;
            this.formats = formats;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DotNode dotNode = (DotNode) o;

            return key.equals(dotNode.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    public static class DotBuilder {

        Path file;

        private String name = "G";
        private boolean isDigraph = true;
        private final Set<DotNode> nodes = new HashSet<>();
        private final Map<Tuple<String, String>, Integer> edges = new HashMap<>();    // keys...connected nodes; values...how many connections between these two nodes
        private final HashMap<String, List<String>> nodeAttributes = new HashMap<>();

        public DotBuilder(Path file) {
            this.file = file;
        }

        public DotBuilder setName(String name) {
            this.name = name;
            return this;
        }

        public DotBuilder setDigraph(boolean isDigraph) {
            this.isDigraph = isDigraph;
            return this;
        }

        public void addNode(String n, String[] formattings) {
            nodes.add(new DotNode(n, formattings));
        }

        public boolean containsEdge(Tuple<String, String> edge) {
            return edges.containsKey(edge);
        }

        public boolean containsEdge(String n1, String n2) {
            return containsEdge(new Tuple<>(n1, n2));
        }

        public DotBuilder addEdge(Tuple<String, String> tuple) {
            Integer edgeCount = edges.get(tuple);
            if (edgeCount == null) {
                edgeCount = 0;
            }
            edgeCount++;
            edges.put(tuple, edgeCount);
            return this;
        }

        public DotBuilder addEdge(String n1, String n2) {
            return addEdge(new Tuple<>(n1, n2));
        }

        public DotBuilder addEdgeUnique(String n1, String n2) {
            return addEdgeUnique(new Tuple<>(n1, n2));
        }

        public DotBuilder addEdgeUnique(Tuple<String, String> tuple) {
            if (!edges.containsKey(tuple)) {
                edges.put(tuple, 1);
            }
            return this;
        }

        public DotBuilder addNodeAttribute(String node, String attribute) {
            List<String> attributes = nodeAttributes.get(node);
            if (attributes == null) {
                nodeAttributes.put(node, new ArrayList<>());
                attributes = nodeAttributes.get(node);
            }
            attributes.add(attribute);
            return this;
        }

        public void build() throws IOException {
            try (FileWriter fw = new FileWriter(file.toFile(), false); BufferedWriter bw = new BufferedWriter(fw); PrintWriter out = new PrintWriter(bw)) {

                out.print(isDigraph ? "digraph " : "graph ");
                out.print(name);
                out.println(" {");

                for (DotNode node : nodes) {
                    out.print("\"");
                    out.print(node.key.replace("\n", "\\n"));
                    out.print("\"");
                    if (node.formats != null && node.formats.length > 0) {
                        out.print("[");
                        out.print(Arrays.stream(node.formats).collect(Collectors.joining(", ")));
                        out.print("]");
                    }
                    out.println(";");
                }

                edges.forEach((key, value) -> {
                    out.print("\"");
                    out.print(key.a.replace("\n", "\\n"));
                    out.print("\"");
                    out.print(" -> ");
                    out.print("\"");
                    out.print(key.b.replace("\n", "\\n"));
                    out.print("\"");
                    if (value > 1) {
                        out.print("[ label=\" ");
                        out.print(value);
                        out.print("\"]");
                    }
                    out.println(";");
                });

                out.println("}");
                out.flush();
            }
        }
    }

    // Hide
    private Dot() {

    }

    public static DotBuilder builder(Path file) {
        return new DotBuilder(file);
    }
}
