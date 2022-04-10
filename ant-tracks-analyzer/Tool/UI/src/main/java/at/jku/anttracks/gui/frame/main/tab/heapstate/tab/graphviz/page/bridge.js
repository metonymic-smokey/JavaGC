(function () {
    var MAX_BEFORE_COLLAPSE = 3;
    var graph, bridge, randomUUID;
    bridge = {
        init: function (_graph, _randomUUID) {
            graph = _graph;
            randomUUID = _randomUUID;
        },

        graphInput: "digraph {}",

        /*
         * CALLS FROM JAVA
         */
        delegate: {
            getActionItems: function (id) {
                return [id, "action1\ntest\ntest2",
                    {
                        getName: function () {
                            return "action2"
                        },
                        getActions: function () {
                            return [];
                        }
                    },
                    {
                        getName: function () {
                            return "multilevel";
                        }, getActions: function () {
                            return ["multi1", "multi2"];
                        }
                    }
                ]
            },
            actionItemSelected: function (id, action) {
                if (action === id) {

                }
                console.log("actionItemSelected(" + id + "): " + action)
            },
            mouseEntered: function (id) {
                console.log("mouseEntered(" + id + ")")
            },
            mouseLeft: function (id) {
                console.log("mouseEntered(" + id + ")")
            },
            recalculateAndThenRepaintLinkWeights: function (showKeepAliveEdgesForNode) {
                console.log("recalculateAndThenRepaintLinkWeights")
            },
            getDotGraph: function () {
                return "digraph{\"3543\"[shape=\"ellipse\",label=\"Family(4001)\",labelStyle=\"font-family: monospace\"]\n" +
                    "\"15547\"[shape=\"ellipse\",label=\"Object[](1)\",labelStyle=\"font-family: monospace\"]\n" +
                    "\"3544\"[shape=\"rect\",label=\"ArrayList\",style=\"stroke: red\"]\n" +
                    "\"3545\"[shape=\"rect\",label=\"Object[]\"]\"3544\"->\"3545\" \"3545\"->\"3543\" \"15547\"->\"3543\"[label=\"1 object\npoints to\n4001 objects\",style=\"stroke-width: 3.4px\"]}"
            }
        },
        setDelegate: function (delegate) {
            bridge.delegate = delegate;
        },
        repaintGraph: function (centerId, showKeepAliveEdgesForNode) {
            bridge.graphInput = bridge.delegate.getDotGraph();
            graph.redraw(bridge.graphInput, bridge, centerId);
            bridge.delegate.recalculateAndThenRepaintLinkWeights(showKeepAliveEdgesForNode);
        },
        repaintLinkWeights: function (weights) {
            console.log(weights);
            graph.repaintLinkWeights(weights);
        },
        repaintLinkLabels: function (labels) {
            console.log(labels);
            graph.repaintLinkLabels(labels);
        }
    };
    window.bridge = bridge;
})();