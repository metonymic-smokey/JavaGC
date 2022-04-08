(function () {
    window.onload = function () {
        var randomUUID = window.randomUUID;
        var radialMenu = window.radialMenu;
        var graph = window.graph;
        var bridge = window.bridge;

        graph.init(radialMenu);
        bridge.init(graph, randomUUID);
    }
})();