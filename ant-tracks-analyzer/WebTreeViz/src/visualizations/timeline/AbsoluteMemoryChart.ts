import ClassificationTree from '@/dao/ClassificationTree';
import * as d3 from 'd3';
import Helpers from '@/visualizations/util/Helpers';
import VisualizationManager from '@/visualizations/VisualizationManager';
import TimelineSunburst from "@/visualizations/timeline/TimelineSunburst";
import TimelineIcicle from "@/visualizations/timeline/TimelineIcicle";

export default class AbsoluteMemoryChart {
    // Consts
    private readonly marginLeft = 100;
    private readonly marginRight = 25;
    private readonly marginBottom = 35;
    private readonly marginMiddle = 12;
    private readonly textFont = '12px "Source Sans Pro", sans-serif';
    private readonly axisLabelFont = '16px "Source Sans Pro", sans-serif';

    // Sizes
    private readonly _height: number = 200;
    private _width: number = 200;

    // Overall svg
    private svg!: d3.Selection<SVGSVGElement, void, HTMLElement, void>;

    // Line chart
    private chartArea!: d3.Selection<SVGGElement, void, HTMLElement, void>;
    private highlightCircle!: d3.Selection<SVGCircleElement, void, HTMLElement, void>;
    private highlightLine!: d3.Selection<SVGLineElement, void, HTMLElement, void>;
    private xScale!: d3.ScaleLinear<number, number>;
    private yScale!: d3.ScaleLinear<number, number>;
    private yAxis!: d3.Axis<number | { valueOf(): number }>;
    private yAxisSelection!: d3.Selection<SVGGElement, void, HTMLElement, void>;
    private chartLines!: d3.Selection<SVGPathElement, ClassificationTree[], HTMLElement, void>;
    private markers!: d3.Selection<SVGCircleElement, ClassificationTree, SVGGElement, void>;
    private yAxisLabel!: d3.Selection<SVGTextElement, void, HTMLElement, void>;

    // Tree Visualizations
    private treesVizArea!: d3.Selection<SVGGElement, void, HTMLElement, void>;
    private connectionArea!: d3.Selection<SVGGElement, void, HTMLElement, void>;

    public get height() {
        return this._height;
    }

    public get chartHeight() {
        return this.height / 2;
    }

    public get chartHeightWithoutMargins() {
        return this.chartHeight - this.marginMiddle - this.marginBottom;
    }

    public get selectedTreesVizHeight() {
        return this.height - this.chartHeight;
    }

    public get width() {
        return this._width;
    }

    public get widthWithoutMargins() {
        return this._width - this.marginLeft - this.marginRight;
    }

    constructor(private trees: ClassificationTree[], // TODO get rid of this, duplicate from manager
                private pageHeight: number,
                private manager: VisualizationManager) {
        const memoryChart = this;

        this._height = pageHeight;
        const parent = d3.select<HTMLDivElement, void>('#absoluteChart');
        this._width = parent.node()!.clientWidth;
        this.svg = parent.append('svg')
                         .attr('viewBox', `0 0 ${this._width} ${this.height}`)
                         .style('width', '100%')
                         .style('height', this.height + 'px');

        // Helper methods
        function buildChartArea() {
            // Remove possibly existing area
            memoryChart.svg.select("#absolute-memory-chart-chartarea").remove();

            memoryChart.chartArea = memoryChart.svg.append<SVGGElement>('g')
                                               .attr("id", "absolute-memory-chart-chartarea")
                                               .attr('transform', `translate(${memoryChart.marginLeft}, ${memoryChart.marginMiddle + memoryChart.selectedTreesVizHeight})`);

            const minTime = memoryChart.trees[0].time;
            const maxTime = memoryChart.trees[memoryChart.trees.length - 1].time;

            // Add Y axis
            memoryChart.yScale = d3.scaleLinear()
                                   .domain([0, memoryChart.getMaxValue()])
                // this range is reversed, because the lowest value, i.e. 0, should yield the full heightWithoutMargins (to be moved to the bottom)
                                   .range([memoryChart.chartHeightWithoutMargins, 0])
                                   .nice();

            // (1) First draw horizontal lines ...
            let yAxisHorizontalLines = d3.axisLeft(memoryChart.yScale)
                                         .ticks(6)
                                         .tickFormat(() => "")
                                         .tickSizeInner(-memoryChart.widthWithoutMargins)
                                         .tickSizeOuter(0);
            let yAxisHorizontalLinesSelection = memoryChart.chartArea
                                                           .append('g')
                                                           .classed("horizontal-lines", true);
            yAxisHorizontalLinesSelection.call(yAxisHorizontalLines)
                                         .select(".domain")
                                         .remove();

            // (2) ... then draw the y axis ...
            memoryChart.yAxis = d3.axisLeft(memoryChart.yScale)
                                  .ticks(6)
                                  .tickFormat((x) => Helpers.convertCurValToScaledString(x.valueOf(), memoryChart.manager.displayBytes))
            memoryChart.yAxisSelection = memoryChart.chartArea
                                                    .append('g')
                                                    .style('font', memoryChart.textFont);
            memoryChart.yAxisSelection.call(memoryChart.yAxis);

            memoryChart.yAxisLabel = memoryChart.yAxisSelection
                                                .append<SVGTextElement>('text')
                                                .style('font', memoryChart.axisLabelFont)
                                                .style('font-weight', 'bolder')
                                                .attr('fill', 'black');
            memoryChart.updateYAxisLabel();

            // (3) ... then the x-Axis (which puts a black line on top of the gray y-line at 0) ...
            // Add X axis --> it is a date format
            memoryChart.xScale = d3.scaleLinear()
                                   .domain([minTime, maxTime])
                                   .range([0, memoryChart.widthWithoutMargins]);
            // .nice();

            const xAxis = d3.axisBottom(memoryChart.xScale);
            const axisG = memoryChart.chartArea
                                     .append('g')
                                     .attr('transform', `translate(0, ${memoryChart.chartHeightWithoutMargins})`)
                                     .style('font', memoryChart.textFont);
            axisG.call(xAxis);
            axisG.append('text')
                 .attr('transform', `translate(${memoryChart.widthWithoutMargins / 2}, 35)`)
                 .attr('fill', 'black')
                 .style('font', memoryChart.axisLabelFont)
                 .style('font-weight', 'bolder')
                 .text('t[ms]');

            // Add the line
            memoryChart.chartLines = memoryChart.chartArea
                                                .append('path')
                                                .datum(memoryChart.trees)
                                                .attr('fill', 'none')
                                                .attr('stroke', 'steelblue')
                                                .attr('stroke-width', 1.5);


            memoryChart.markers = memoryChart.chartArea
                                             .append('g')
                                             .selectAll<SVGCircleElement, void>('circle')
                                             .data<ClassificationTree>(memoryChart.trees);
            const enteredSet = memoryChart.markers
                                          .enter()
                                          .append('circle')
                                          .attr('fill', 'steelblue')
                                          .attr('r', 3)
                                          .style("cursor", "pointer")
                                          .on("click", (datum, index) => {
                                              memoryChart.manager.toggleTreeOnTimeline(index);
                                              memoryChart.updateVisualization();
                                          });
            memoryChart.markers = memoryChart.markers.merge(enteredSet);

            memoryChart.highlightCircle = memoryChart.chartArea
                                                     .append<SVGCircleElement>('circle')
                                                     .attr('fill', 'red')
                                                     .attr('r', 4);

            memoryChart.highlightLine = memoryChart.chartArea
                                                   .append<SVGLineElement>('line')
                                                   .attr('y1', 0)
                                                   .attr('y2', memoryChart.chartHeightWithoutMargins)
                                                   .attr('stroke', 'red')
                                                   .attr('stroke-dasharray', '4');
            memoryChart.updateCirclesAndLine();
        }

        function buildConnectionArea() {
            // Remove possible existing area
            memoryChart.svg.select("#absolute-memory-chart-connectionarea").remove();

            memoryChart.connectionArea = memoryChart.svg
                                                    .append<SVGGElement>('g')
                                                    .attr("id", "absolute-memory-chart-connectionarea");
        }

        function buildTreesVizArea() {
            // Remove possible existing area
            memoryChart.svg.select('#absolute-memory-chart-treesvizarea').remove();

            memoryChart.treesVizArea = memoryChart.svg
                                                  .append<SVGGElement>('g')
                                                  .attr("id", "absolute-memory-chart-treesvizarea")
                                                  .attr('transform', `translate(${memoryChart.marginLeft}, 0)`);

            const background = memoryChart.treesVizArea
                                          .append("rect")
                                          .attr("x", 0)
                                          .attr("y", 0)
                                          .attr("width", memoryChart.widthWithoutMargins)
                                          .attr("height", memoryChart.selectedTreesVizHeight)
                                          .style("fill", "#ededed")
                                          .style("stroke", "#ededed");
        }

        function buildButtonArea() {
            const xmargin = 10;

            // Remove possibly existing area
            memoryChart.svg.select("#absolute-memory-chart-buttonarea").remove();

            const buttonArea = memoryChart.svg.append("g").attr("id", "absolute-memory-chart-buttonarea");

            const addButton = function (text: string, yPos: number, f: Function) {
                buttonArea
                    .append('g')
                    .attr('transform', `translate(${xmargin}, ${yPos * 25})`)
                    .call(function (g) {
                        // Button rect
                        g.append("rect")
                         .attr("width", memoryChart.marginLeft - 2 * xmargin)
                         .attr("height", 20)
                         .attr("stroke", "black")
                         .classed("button", true)
                         .style("cursor", "pointer")
                         .on("click", function () {
                             f();
                             memoryChart.svg.selectAll(".button").classed("selected-button", false);
                             d3.select(this).classed("selected-button", true);
                         })
                         .on("mouseover", function () {
                             d3.select(this).classed("hovered-button", true);
                         })
                         .on("mouseout", function (d) {
                             d3.select(this).classed("hovered-button", false);
                         });
                    })
                    .call(function (g) {
                        // Button text
                        g.append("text")
                         .attr("x", 3)
                         .attr("y", 15)
                         .style('pointer-events', 'none')
                         .text(text);
                    })
            }

            addButton("Loc. Sun.", 0, function () {
                memoryChart.manager.shownTimelineVisualizationType = 1;
                memoryChart.updateVisualization();
            });
            addButton("Glob. Sun.", 1, function () {
                memoryChart.manager.shownTimelineVisualizationType = 2;
                memoryChart.updateVisualization();
            });
            addButton("Loc. Ice.", 2, function () {
                memoryChart.manager.shownTimelineVisualizationType = 3;
                memoryChart.updateVisualization();
            });
            addButton("Glob. Ice.", 3, function () {
                memoryChart.manager.shownTimelineVisualizationType = 4;
                memoryChart.updateVisualization();
            });
        }

        buildTreesVizArea();
        buildConnectionArea();
        buildChartArea();
        buildButtonArea();
    }

    public changeMetric() {
        this.updateYScale();
        this.updateYAxisLabel();
        this.updateCirclesAndLine();
        this.manager.updateTimelineData();
        this.updateVisualization();
    }

    public changeSortingMode() {
        this.manager.updateTimelineData();
        this.updateVisualization();
    }

    public updateVisualization() {
        const memoryChart = this;

        memoryChart.updateCurrentTime();

        function updateTreeVizArea() {
            //console.log(`update timeline tree visualizations: bytes = ${memoryChart.manager.displayBytes}, sorting = ${memoryChart.manager.selectedSortingMode}`);

            const treeVisualizations = memoryChart.treesVizArea
                                                  .selectAll(".treeViz")
                                                  .data<TimelineIcicle | TimelineSunburst>(memoryChart.manager.selectedTimelineTrees, function (datum) {
                                                      const typesafeDatum = datum as (TimelineIcicle | TimelineSunburst);
                                                      return `${typesafeDatum.idx}-${typesafeDatum.showBytes}-${typesafeDatum.sortingMode}-${typesafeDatum.isLocal}-${typesafeDatum.constructor.name}`;
                                                  })
                                                  .join(function (enter) {
                                                      const newTreeViz = enter.append("g")
                                                                              .classed("treeViz", true)
                                                                              .attr("transform", (datum, index) => `translate(${memoryChart.selectedTreesVizHeight * (index + 0.5)},0) scale(0)`)
                                                                              .attr("id",
                                                                                    datum => `timeline-${datum.idx}-${memoryChart.manager.displayBytes}-${memoryChart.manager.selectedSortingMode}`);

                                                      /*
                                                       newTreeViz.append("rect")
                                                       .attr("x", 0)
                                                       .attr("y", 0)
                                                       .attr("width", memoryChart.selectedTreesVizHeight)
                                                       .attr("height", memoryChart.selectedTreesVizHeight)
                                                       .style("fill", "lightyellow")
                                                       .style("stroke", "gray")
                                                       .style("stroke-width", "1");
                                                       */
                                                      return newTreeViz;
                                                  })
                                                  .each(function (datum, index) {
                                                      datum.draw(<SVGGElement>this);
                                                  })
                                                  .transition()
                                                  .duration(1000)
                                                  .ease(d3.easeLinear)
                                                  .attr("transform", (datum, index) => `translate(${memoryChart.selectedTreesVizHeight * index}, 0) scale(1)`);
            treeVisualizations
                //.select to push down possible new data
                .select("text")
                .text(datum => `Fake tree viz  of index ${datum}`);
        }

        function updateConnectionArea() {
            const topOffset = memoryChart.selectedTreesVizHeight + memoryChart.marginMiddle;

            memoryChart.connectionArea
                       .selectAll(".connection")
                       .data(memoryChart.manager.timelineTreeIndices, datum => `${datum}`)
                       .join(function (enter) {
                           const newConnection =
                               enter.append("line")
                                    .classed("connection", true)
                                    .style("stroke", "red")
                                    .style("stroke-width", 2.5)
                                   // Both use the same value because the transition (see below) will strech the lines to the correct position
                                    .attr("x1", (datum) => memoryChart.marginLeft + <number>memoryChart.xScale(memoryChart.trees[datum].time))
                                    .attr("y1", (datum) => topOffset + <number>memoryChart.yScale(Helpers.getValueFromTree(memoryChart.trees[datum], memoryChart.manager.displayBytes)))
                                    .attr("x2", (datum) => memoryChart.marginLeft + <number>memoryChart.xScale(memoryChart.trees[datum].time))
                                    .attr("y2", (datum) => topOffset + <number>memoryChart.yScale(Helpers.getValueFromTree(memoryChart.trees[datum], memoryChart.manager.displayBytes)));
                           return newConnection;
                       })
                       .transition()
                       .duration(1000)
                       .ease(d3.easeLinear)
                       .attr("x1", (datum) => memoryChart.marginLeft + <number>memoryChart.xScale(memoryChart.trees[datum].time))
                       .attr("y1", (datum) => topOffset + <number>memoryChart.yScale(Helpers.getValueFromTree(memoryChart.trees[datum], memoryChart.manager.displayBytes)))
                       .attr("x2", (datum, index) => memoryChart.marginLeft + memoryChart.selectedTreesVizHeight * (index + 0.5))
                       .attr("y2", memoryChart.selectedTreesVizHeight - 2);

        }

        updateTreeVizArea();
        updateConnectionArea();
    }

    private updateCurrentTime() {
        this.highlightCircle
            .attr('cx', <number>this.xScale(this.manager.getCurTree().time))
            .attr('cy', <number>this.yScale(Helpers.getValueFromTree(this.manager.getCurTree(), this.manager.displayBytes)));

        this.highlightLine
            .attr('x1', <number>this.xScale(this.manager.getCurTree().time))
            .attr('x2', <number>this.xScale(this.manager.getCurTree().time));
    }


    private getMaxValue(): number {
        return Helpers.max(this.trees.map(x => Helpers.getValueFromTree(x, this.manager.displayBytes)));
    }

    private updateYScale() {
        this.yScale
            .domain([0, this.getMaxValue()])
            .nice();
        this.yAxisSelection.call(this.yAxis);
    }

    private updateYAxisLabel() {
        this.yAxisLabel.text(Helpers.getCurAxisLabel(this.manager.displayBytes));
        const length = this.yAxisLabel.node()!.getComputedTextLength();
        this.yAxisLabel.attr('transform', `translate(-50, ${this.chartHeightWithoutMargins / 2 - length / 2}) rotate(-90)`);
    }

    private updateCirclesAndLine() {
        this.markers.attr('cx', d => <number>this.xScale(d.time))
            .attr('cy', d => <number>this.yScale(Helpers.getValueFromTree(d, this.manager.displayBytes)));
        this.chartLines.attr('d', d3.line<ClassificationTree>()
                                    .x((d) => <number>this.xScale(d.time))
                                    .y((d) => <number>this.yScale(Helpers.getValueFromTree(d, this.manager.displayBytes)))
        );
    }
}
