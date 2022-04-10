(function () {
    function createSvgArc(r, startAngle, endAngle) {
        var start = ((startAngle < endAngle ? startAngle : endAngle) + Math.PI * 2) % (Math.PI * 2);
        var end = ((startAngle < endAngle ? endAngle : startAngle) + Math.PI * 2) % (Math.PI * 2);

        if (end === start) {
            // draw a whole circle
            return [
                'M', -r, 0,
                'A', r, r, 0, 1, 0, r, 0,
                'A', r, r, 0, 1, 0, -r, 0]
                .join(' ');
        } else {
            var largeArc = end - start <= Math.PI ? 0 : 1;

            // draw a circle sector
            return [
                'M', 0, 0,
                'L', Math.cos(start) * r, -Math.sin(start) * r,
                'A', r, r, 0, largeArc, 0, Math.cos(end) * r, -Math.sin(end) * r,
                'L', 0, 0]
                .join(' ');
        }
    }

    window.radialMenu = {
        create: function (g, r, actions) {
            var outerR = Math.max(r * 2, r + 50);
            var actionData = d3.select(g).selectAll("path.radialMenu")
                .data(actions)
                .enter();

            var circleSectors = actionData.append("path");

            var actionTextGroups = actionData.append("g");

            var actionTexts = actionTextGroups.append("text")
                .attr("class", "radialMenu")
                .attr("x", 0)
                .attr("y", 0)
                .each(function (d) {
                    var text = d;
                    if (typeof d === "object") {
                        text = d.getName();
                    }
                    text = ("" + text).split("\n");
                    d3.select(this).selectAll("tspan").data(text).enter().append("tspan")
                        .attr("x", 0)
                        .attr("dy", function (d, i) {
                            if (i !== 0) {
                                return "1.2em";
                            }
                            return null;
                        })
                        .text(function (d) {
                            return d;
                        });
                });

            actionTexts.each(function () {
                var bbox = this.getBBox();
                outerR = Math.max(outerR, r + 15 + Math.sqrt(bbox.width * bbox.width + bbox.height * bbox.height));
            });

            // circle sectors
            circleSectors
                .attr("class", "radialMenu")
                .attr("d", function (d, i) {
                    return createSvgArc(outerR,
                        Math.PI / 2 - (2 * i * Math.PI / actions.length),
                        Math.PI / 2 - (2 * (i + 1) * Math.PI / actions.length));
                })
                .on("click", function (d) {
                    if (typeof d === "object") {
                        var actions = d.getActions()
                        if (!!actions && (actions.length > 0)) {
                            var bbox = this.getBBox();
                            var radius = Math.sqrt(bbox.width * bbox.width + bbox.height * bbox.height) / 2;
                            window.radialMenu.create(d3.select(this.parentNode).append("g").attr("class", "radialMenu").attr("id", d3.select(g).attr("id")).node(), radius, d.getActions());
                            d3.event.stopPropagation();
                        } else {
                            bridge.delegate.actionItemSelected(parseInt(d3.select(g).attr("id")), d.getName());
                        }
                    } else {
                        bridge.delegate.actionItemSelected(parseInt(d3.select(g).attr("id")), d);
                    }
                });

            // text of circle sectors
            actionTextGroups
                .attr("transform", function (d, i) {
                    var x = Math.cos(Math.PI / 2 - (2 * i * Math.PI + Math.PI) / actions.length) * (r + (outerR - r) / 2);
                    var y = -Math.sin(Math.PI / 2 - (2 * i * Math.PI + Math.PI) / actions.length) * (r + (outerR - r) / 2);
                    return "translate(" + x + "," + y + ")"
                });

            if (actions.length > 0) {
                // whiten out inner area of circle sectors
                d3.select(g)
                    .append("circle")
                    .attr("cx", 0)
                    .attr("cy", 0)
                    .attr("r", r + 5)
                    .style("fill", "white")
                    .style("stroke", "none");

                // lightsteelblue border of circle sectors
                d3.select(g)
                    .append("circle")
                    .attr("cx", 0)
                    .attr("cy", 0)
                    .attr("r", outerR)
                    .style("fill", "none")
                    .style("stroke", "lightsteelblue")
                    .style("stroke-width", "2")
                    .style("pointer-events", "none");
            }
        }
    };
})();