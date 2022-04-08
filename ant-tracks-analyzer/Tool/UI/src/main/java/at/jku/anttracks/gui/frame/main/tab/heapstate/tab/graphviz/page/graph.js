(function () {
    var radialMenu, inner, zoom, svg, render, g, graph;
    graph = {
        init: function (_radialMenu) {
            radialMenu = _radialMenu;

            // Set up zoom support
            inner = d3.select("svg g");
            zoom = d3.behavior.zoom().on("zoom", function () {
                inner.attr("transform", "translate(" + d3.event.translate + ")" + "scale(" + d3.event.scale + ")");
            });
            svg = d3.select("svg")
                .on("click", function () {
                    // if user clicks anywhere, remove the radial menu
                    d3.select("g.radialMenu").remove();
                })
                .attr("width", function () {
                    return window.innerWidth - 10;
                })
                .attr("height", function () {
                    return window.innerHeight - 10;
                })
                .attr("viewBox", function () {
                    var boundingRect = this.getBoundingClientRect();
                    return [boundingRect.width / -2, boundingRect.height / -2, boundingRect.width, boundingRect.height].join(" ");
                })
                .call(zoom);

            d3.selectAll("button.zoom").on("click", function () {
                var zoomF = Math.pow(1.5, parseInt(d3.select(this).attr("zoom-data")));
                var translate = zoom.translate();
                translate[0] *= zoomF;
                translate[1] *= zoomF;
                var scale = zoom.scale() * zoomF;
                svg.call(zoom.scale(scale).translate(translate).event);
            });

            d3.select("button.center").on("click", function () {
                var bbox = inner.node().getBBox();
                var clientRect = svg.node().getBoundingClientRect();
                var zoomFactor = Math.min(clientRect.width / bbox.width, clientRect.height / bbox.height);
                svg.call(zoom.scale(zoomFactor).translate([
                    (-bbox.width / 2 - bbox.x) * zoomFactor,
                    (-bbox.height / 2 - bbox.y) * zoomFactor
                ]).event);
            });

            // Create and configure the renderer
            render = dagreD3.render();

            window.addEventListener("resize", function () {
                svg
                    .attr("width", function () {
                        return window.innerWidth - 10;
                    })
                    .attr("height", function () {
                        return window.innerHeight - 10;
                    })
                    .attr("viewBox", function () {
                        var boundingRect = this.getBoundingClientRect();
                        return [boundingRect.width / -2, boundingRect.height / -2, boundingRect.width, boundingRect.height].join(" ");
                    });
            });
        },
        redraw: function (graphInput, bridge, centerId) {
            try {
                g = graphlibDot.read(graphInput);
                g.setGraph({rankdir: 'TB', edgesep: 10, ranksep: 30, nodesep: 20});
            } catch (e) {
                console.log(e);
                throw e;
            }

            // Set margins, if not present
            if (!g.graph().hasOwnProperty("marginx") &&
                !g.graph().hasOwnProperty("marginy")) {
                g.graph().marginx = 20;
                g.graph().marginy = 20;
            }

            g.graph().transition = function (selection) {
                var centerDone = false;
                return selection.transition().duration(500).each("end", function () {
                    selection.filter(function (d) {
                        return d === centerId;
                    }).each(function () {
                        if (!centerDone) {
                            centerDone = true;
                            var translateNode = d3.transform(d3.select(this).attr("transform")).translate;
                            var scale = d3.transform(inner.attr("transform")).scale;
                            svg.call(zoom.translate([
                                (-translateNode[0] * scale[0]),
                                (-translateNode[1] * scale[1])
                            ]).event);
                        }
                    });
                });
            };

            // Render the graph into svg g
            inner.call(render, g);

            //make nodes clickable  and enterable
            inner.selectAll(".node")
                .on("click", function (id) {
                    // create radial menu
                    d3.event.stopPropagation();
                    d3.select("g.radialMenu").remove();
                    var bbox = this.getBBox();
                    var radius = Math.sqrt(bbox.width * bbox.width + bbox.height * bbox.height) / 2;
                    this.parentNode.appendChild(this);
                    var menu = d3.select(this.parentNode)
                        .append("g")
                        .attr("id", id)
                        .attr("class", "radialMenu")
                        .attr("transform", d3.select(this).attr("transform"))
                        .node();
                    menu.parentNode.insertBefore(menu, this);
                    radialMenu.create(menu, radius, bridge.delegate.getActionItems(parseInt(id)))
                })
                .on("mouseenter", function (id) {
                    if (!isNaN(parseFloat(id))) {
                        bridge.delegate.mouseEntered(parseInt(id));
                    }
                })
                .on("mouseleave", function (id) {
                    if (!isNaN(parseFloat(id))) {
                        bridge.delegate.mouseLeft(parseInt(id));
                    }
                });

            //make paths hoverable
            inner.selectAll(".edgePath")
                .each(function () {
                    var edgePath = d3.select(this);
                    edgePath.selectAll("path.hover").remove();
                    edgePath.selectAll("path.path").each(function () {
                        var path = d3.select(this);
                        edgePath.append("path")
                            .attr("class", "hover")
                            .attr("d", path.attr("d"));
                    });
                })
                .on("mouseenter", function (data) {
                    inner.selectAll(".node").filter(function (id) {
                        return data.v === id || data.w === id;
                    }).classed("hovered", true);
                    inner.selectAll(".edgeLabel").filter(function (ids) {
                        return data.v === ids.v && data.w === ids.w;
                    }).classed("hovered", true);
                    d3.select(this).classed("hovered", true)
                })
                .on("mouseleave", function (data) {
                    inner.selectAll(".node").filter(function (id) {
                        return data.v === id || data.w === id;
                    }).classed("hovered", false);
                    inner.selectAll(".edgeLabel").filter(function (ids) {
                        return data.v === ids.v && data.w === ids.w;
                    }).classed("hovered", false);
                    d3.select(this).classed("hovered", false)
                });

            // add background to edge labels
            inner.selectAll(".edgeLabel").selectAll("g").each(function () {
                var labelGroup = d3.select(this);

                var bbox = this.getBBox();

                labelGroup.insert("rect", ":first-child")
                    .attr("x", bbox.x)
                    .attr("y", bbox.y)
                    .attr("width", bbox.width)
                    .attr("height", bbox.height);
            });
            setTimeout(function () {
                inner.selectAll(".edgePath").each(function () {
                    var resultPath1 = "";
                    var resultPath2 = "";
                    var path = d3.select(this).select("path.path").attr("d").substring(1).split("L");
                    var points = path.map(function (pos) {
                        var pts = pos.split(",");
                        return [parseFloat(pts[0]), parseFloat(pts[1])];
                    });
                    for (var i = 0; i < points.length; i++) {
                        resultPath1 += "L" + (points[i][0] - 10) + "," + points[i][1];
                        resultPath2 += "L" + (points[points.length - i - 1][0] + 10) + "," + points[points.length - i - 1][1];
                    }
                    d3.select(this).select("path.hover").attr("d", "M" + resultPath1.substring(1) + resultPath2);
                })
            }, 1000);
        },
        repaintLinkWeights: function (weights) {
            inner.selectAll(".edgePath").each(function (data) {
                var weightInfo = weights.find(function (weight) {
                    return weight.v === data.v && weight.w === data.w
                });
                if (typeof weightInfo !== 'undefined') {
                    // Line Size, Line Color, Arrow Size
                    d3.select(this).select("path.path").style("stroke-width", weightInfo.value + "px").style('stroke', 'rgb(' + Math.round((weightInfo.value - 1) / 5.0 * 255.0) + ',0,0)');
                    // Arrow Color
                    d3.select(this).select("defs marker path").style('stroke', 'rgb(0,0,0)').style('fill', 'rgb(' + Math.round((weightInfo.value - 1) / 5.0 * 255.0) + ',0,0)')
                }
            });
        },
        repaintLinkLabels: function (labels) {
            inner.selectAll(".edgeLabel").each(function (data) {
                console.log(data);
                var labInfo = labels.find(function (lab) {
                    return lab.v === data.v && lab.w === data.w;
                });
                if (typeof labInfo !== 'undefined') {
                    console.log("link to update found from " + labInfo.v + " to " + labInfo.w);
                    var edgeLabelElement = d3.select(this);
                    var textElement = edgeLabelElement.select("g text");
                    textElement.text(labInfo.value);
                }
            });
        }
    };
    window.graph = graph;
})();