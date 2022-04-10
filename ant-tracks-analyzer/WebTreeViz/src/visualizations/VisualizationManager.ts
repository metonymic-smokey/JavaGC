import Visualization from '@/visualizations/base/Visualization';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import ClassificationTree from '@/dao/ClassificationTree';
import * as d3 from 'd3';
import {HierarchyNode, HierarchyRectangularNode, ScaleOrdinal} from 'd3';
import Helpers from '@/visualizations/util/Helpers';
import AbsoluteMemoryChart from '@/visualizations/timeline/AbsoluteMemoryChart';
import Constants from '@/visualizations/util/Constants';
import AlertManager from '@/visualizations/AlertManager';
import {SortingMode} from '@/dao/SortingMode';
import NodeKeyToUniqueNumberMapper from '@/dao/NodeKeyToUniqueNumberMapper';
import {VisualizationType} from '@/dao/VisualizationType';
import TimelineIcicle from "@/visualizations/timeline/TimelineIcicle";
import TimelineSunburst from "@/visualizations/timeline/TimelineSunburst";
import {TimelineVisualizationType} from "@/dao/TimelineVisualizationType";
// Load the full build.
var _ = require('lodash');

export default class VisualizationManager {
    // Consts
    private otherNodeColor!: string;

    // Settings
    public displayBytes = true;
    public selectedSortingMode = SortingMode.AbsoluteGrowth;
    public shownVisualizationType = VisualizationType.Sunburst;
    private shownVisualizationTypeInTimeline = TimelineVisualizationType.GlobalIcicle;

    // Trees
    public originalTrees!: ClassificationTree[];
    public curTrees!: ClassificationTree[];


    // Time Travel
    private _curDisplayedTreeIdx = 0;
    private curNode!: ClassificationTreeNode;

    // Timeline
    private selectedTreeIndices = new Set<number>();
    public allTimelineTrees: TimelineIcicle[] | TimelineSunburst[] = [];

    // Visualizations
    private visualizations!: Visualization[];
    private memChart!: AbsoluteMemoryChart;
    private slider!: HTMLInputElement;
    private visualizationTypeToDom = new Map<VisualizationType, d3.Selection<HTMLDivElement, void, HTMLElement, void>>();

    // Metadata
    private maxTreeObjects!: number;
    private maxTreeBytes!: number;
    private color!: ScaleOrdinal<string, string>;
    private nodeKeyToUniqueNumberMapper = new NodeKeyToUniqueNumberMapper();
    // --- Sorting
    private absGrowthBytesMap = new Map<string, number>();
    private absGrowthObjectsMap = new Map<string, number>();
    private valueMapBasedOnSelectedSortingMode = new Map<string, number>();

    // Breadcrumb
    protected breadCrumbG!: d3.Selection<SVGGElement, void, HTMLElement, void>;
    private breadcrumbs!: d3.Selection<SVGGElement, ClassificationTreeNode, SVGElement, void>;
    private breadcrumbTextWidths: number[] = [];
    private breadcrumbTextMargins: number[] = [0]; // = cumulative sum of breadcrumbTextWidths, the first offset is 0 however
    private breadcrumbHeight = 30;
    private breadcrumbSpacing = 3;
    private breadcrumbTipTail = 10;
    private breadcrumbSvgWidth!: number;
    private breadcrumbLineOffset = 0;

    // Helper methods as lambdas
    public readonly GLOBAL_NODE_ONLY_SELF_HIGHLIGHT_SELECTION = (n: HierarchyRectangularNode<ClassificationTreeNode>) => this.curNode.idString === n.data.idString;
    public readonly DEFAULT_GLOBAL_HIGHLIGHT_SELECTION =
        (n: HierarchyRectangularNode<ClassificationTreeNode>) => Helpers.isDescendantOrSame(this.curNode.idString, n.data.idString) || Helpers.isAncestorOrSame(this.curNode.idString, n.data.idString);

    init(originalTrees: ClassificationTree[]) {
        this.originalTrees = originalTrees;

        Helpers.calculateAbsGrowthPerNodeRecursive(this.originalTrees[0].root, this.originalTrees[this.originalTrees.length - 1].root, true, this.absGrowthBytesMap);
        Helpers.calculateAbsGrowthPerNodeRecursive(this.originalTrees[0].root, this.originalTrees[this.originalTrees.length - 1].root, false, this.absGrowthObjectsMap);

        this.updateTrees();
        this.visualizations = [];

        this.initVisualizationTypeToDomMapping();

        AlertManager.init();
        this.maxTreeBytes = Helpers.max(this.originalTrees.map(x => x.root.bytes));
        this.maxTreeObjects = Helpers.max(this.originalTrees.map(x => x.root.objects));

        const canvasSelection = d3.create<HTMLCanvasElement>('canvas');
        const canvasContext = canvasSelection.node()!.getContext('2d')!;
        canvasContext.font = Constants.FONT;
        canvasContext.font = '700 14px Source Sans Pro,sans-serif';
        Helpers.setCanvasContext(canvasContext);

        // register click listeners
        d3.select<HTMLButtonElement, void>('#nextDataBtn')
          .on('click', () => this.displayNextTree());

        d3.select<HTMLButtonElement, void>('#previousDataBtn')
          .on('click', () => this.displayPreviousTree());

        // select all inputs inside element with id "metricSelection"
        const metricSelectionRadioButtons = d3.selectAll<HTMLInputElement, void>('#metricSelection input');
        metricSelectionRadioButtons.on('click', (datum, idx, nodes) => this.switchMetric(idx, nodes));

        d3.select<HTMLSelectElement, void>('#sortingModeSelection').on('change', (datum, idx, nodes) => this.changeSortingMode(idx, nodes));//attache listener

        this.curNode = this.curTrees[0].root;

        this.initSlider();
        this.assignColors();
        this.initBreadcrumbs();
    }

    private initVisualizationTypeToDomMapping() {
        this.visualizationTypeToDom.set(VisualizationType.Sunburst, d3.selectAll<HTMLDivElement, void>('.sunburst'));
        this.visualizationTypeToDom.set(VisualizationType.Icicle, d3.selectAll<HTMLDivElement, void>('.icicle'));
        this.visualizationTypeToDom.set(VisualizationType.Treemap, d3.selectAll<HTMLDivElement, void>('.treemap'));
        this.visualizationTypeToDom.set(VisualizationType.HorizontalBarChart, d3.selectAll<HTMLDivElement, void>('.stackedBarChartContainer'));
        for (const [key, value] of this.visualizationTypeToDom) {
            if (key !== this.shownVisualizationType) {
                value.style('display', 'none');
            }
        }
    }

    private showVisualizationType(newSelectedType: VisualizationType) {
        if (newSelectedType !== this.shownVisualizationType) {
            // hide old chart
            this.visualizationTypeToDom.get(this.shownVisualizationType)!.style('display', 'none');
            // show selected chart
            this.visualizationTypeToDom.get(newSelectedType)!.style('display', null);
            this.shownVisualizationType = newSelectedType;
            this.displayCurrentTree();
        }
    }

    public getMaxValueForSelectedMetric(): number {
        return this.displayBytes ? this.maxTreeBytes : this.maxTreeObjects;
    }

    public getScalingFactor(treeRoot: ClassificationTreeNode): number {
        const maxTreeSize = this.getMaxValueForSelectedMetric();
        const curTreeSize = Helpers.getCurValueForNode(treeRoot, this.displayBytes);
        return curTreeSize / maxTreeSize;
    }

    public getCurTree(): ClassificationTree {
        return this.curTrees[this.curDisplayedTreeIdx];
    }

    get curDisplayedTreeIdx(): number {
        return this._curDisplayedTreeIdx;
    }

    public getCurDisplayedNode(): ClassificationTreeNode {
        return this.curNode;
    }

    private switchMetric(idx: number, nodes: HTMLInputElement[] | ArrayLike<HTMLInputElement>) {
        const selected = nodes[idx];
        const useBytes = selected.value === 'bytes';
        if (useBytes !== this.displayBytes) {
            this.displayBytes = useBytes;

            this.performUpdateAfterChangingOption();
            this.memChart.changeMetric();
            this.updateTimeInfo();
        }
    }

    private performUpdateAfterChangingOption() {
        this.updateTrees();

        this.assignColors();

        this.updateDisplayedNodeToClosestExistingOne();

        for (let vis of this.visualizations) {
            vis.treesOrSortingChanged();
        }
    }

    private changeSortingMode(idx: number, nodes: HTMLSelectElement[] | ArrayLike<HTMLSelectElement>) {
        const selectElem = nodes[idx];
        const selectedMode: SortingMode = Helpers.getSortingModeForValue(parseInt(selectElem.value));
        if (selectedMode !== this.selectedSortingMode) {
            // sortingMode changed
            this.selectedSortingMode = selectedMode;

            this.performUpdateAfterChangingOption();
        }

        this.memChart.changeSortingMode();
    }

    private updateTrees() {
        this.curTrees = this.createGlobalTrees();
    }

    private createGlobalTrees(): ClassificationTree[] {
        this.valueMapBasedOnSelectedSortingMode.clear();
        let treeUsedForSelection: ClassificationTree;
        let reducedTrees;

        const originalTreeCopy = Helpers.cloneTrees(this.originalTrees);

        // this basically is a process with three steps for each mode
        // 1. calculate metric
        // 2. use (1) to select keys based on specific tree
        // 3. reduce all trees based on the selected keys from (2)
        if (this.selectedSortingMode === SortingMode.StartSize) {
            treeUsedForSelection = originalTreeCopy[0];
            this.valueMapBasedOnSelectedSortingMode = Helpers.calculateSizeAtSpecificTree(treeUsedForSelection.root, this.displayBytes);
        } else if (this.selectedSortingMode === SortingMode.EndSize) {
            treeUsedForSelection = originalTreeCopy[originalTreeCopy.length - 1];
            this.valueMapBasedOnSelectedSortingMode = Helpers.calculateSizeAtSpecificTree(treeUsedForSelection.root, this.displayBytes);
        } else if (this.selectedSortingMode === SortingMode.AbsoluteGrowth) {
            // if a node is not present in the lastTree, its maximum growth can be 0 and thus it is okay if we don't select that node
            // => we can perform the nodeSelection based on the last tree (using the growthPerKey Map)
            treeUsedForSelection = originalTreeCopy[originalTreeCopy.length - 1];
            // Helpers.calculateAbsGrowthPerNodeRecursive(originalTreeCopy[0].root, treeUsedForSelection.root, this.displayBytes, valueMap);

            if (this.displayBytes) {
                this.valueMapBasedOnSelectedSortingMode = new Map<string, number>(this.absGrowthBytesMap);
            } else {
                this.valueMapBasedOnSelectedSortingMode = new Map<string, number>(this.absGrowthObjectsMap);
            }
        } else {
            console.error('unsupported sorting mode');
            return null!;
        }

        reducedTrees = this.selectNodesAndReduceTrees(originalTreeCopy, this.displayBytes, treeUsedForSelection);

        // some nodes of these trees might not have unique numbers yet, we also cannot just use the selectedNodes set, to add the unique numbers
        // because it is possible, that 'other' series were generated while reducing and these also need unique numbers...
        this.nodeKeyToUniqueNumberMapper.createUniqueNumberForEachNodeForEachTree(reducedTrees);
        return reducedTrees;
    }

    public selectNodesAndReduceTrees(trees: ClassificationTree[], useBytes: boolean, treeUsedForSelection: ClassificationTree): ClassificationTree[] {
        const lastTree = trees[trees.length - 1];
        const result = new Array<ClassificationTree>();

        const reduceResult = Helpers.reduceTree(treeUsedForSelection.root, useBytes, this.valueMapBasedOnSelectedSortingMode);
        const convertedTree = reduceResult.reducedRootNode;
        const keptKeys = reduceResult.selectedKeys;


        // next step: create all the other trees based on the selectedKeys set - and move all other, not selected, nodes into the 'other' node
        for (let i = 0; i < trees.length; i++) {
            const tree = trees[i];
            if (tree.time === treeUsedForSelection.time) {
                // insert already converted tree
                result.push(new ClassificationTree(lastTree.classifiers, lastTree.time, convertedTree));
            } else {
                result.push(new ClassificationTree(tree.classifiers,
                                                   tree.time,
                                                   Helpers.reduceTreeBasedOnSelectedNodeSetRecursively(tree.root, useBytes, keptKeys, this.valueMapBasedOnSelectedSortingMode)));
            }
        }

        return result;
    }

    // is called after the visualizations have been added
    public lateInit(): void {
        this.updateTimelineData();
        this.updateTimeInfo();

        d3.select<HTMLButtonElement, void>('#navigateSunburst')
          .on('click', () => this.showVisualizationType(VisualizationType.Sunburst));

        d3.select<HTMLButtonElement, void>('#navigateIcicle')
          .on('click', () => this.showVisualizationType(VisualizationType.Icicle));

        d3.select<HTMLButtonElement, void>('#navigateTreemap')
          .on('click', () => this.showVisualizationType(VisualizationType.Treemap));


        d3.select<HTMLButtonElement, void>('#navigateBarChart')
          .on('click', () => this.showVisualizationType(VisualizationType.HorizontalBarChart));

        d3.select<HTMLDivElement, void>('#loadingIndicator').remove();
    }

    private initSlider() {
        // <input type="range" min="1" max="100" value="50" class="slider" id="myRange">
        this.slider = d3.select<HTMLDivElement, any>('.timeSliderContainer')
                        .append<HTMLInputElement>('input')
                        .attr('type', 'range')
                        .attr('min', 0)
                        .attr('max', (this.curTrees.length - 1))
                        .attr('value', 0)
                        .attr('class', 'slider')
                        .attr('id', 'timeSlider').node()!;

        this.slider.addEventListener('input', (ev) => this.sliderValChanged());
    }

    private sliderValChanged(newVal?: Event, oldVal?: any): void {
        this.displayTree(this.slider.valueAsNumber);
    }

    private initBreadcrumbs() {
        this.breadcrumbSvgWidth = window.innerWidth - 20; // -20 because content div has 10px on both sides
        // Add the svg area.
        const trail = d3.select<HTMLDivElement, void>('#breadcrumbContainer')
                        .append<SVGElement>('svg:svg')
                        .attr('width', this.breadcrumbSvgWidth)
                        .attr('height', 30)
                        .attr('id', 'trail');

        this.breadCrumbG = trail.append('g');
        // .attr('transform', 'translate(' + halfWidth + ',' + halfWidth + ')');

        const emptyBreadcrumbs: ClassificationTreeNode[] = [];

        this.breadcrumbs = this.breadCrumbG.selectAll<SVGGElement, void>('g')
                               .data<ClassificationTreeNode>(emptyBreadcrumbs);
        // Add the label at the end, for the percentage.
        this.breadCrumbG.append('svg:text')
            .attr('id', 'endlabel')
            .style('fill', '#000');

        this.updateBreadcrumbs(this.curTrees[this.curDisplayedTreeIdx].root);
    }

    // Generate a string that describes the points of a breadcrumb polygon.
    private breadcrumbPoints(idx: number) {
        let points = [];
        const curWidth = this.breadcrumbTextWidths[idx];
        points.push('0,0');
        points.push(curWidth + ',0');
        points.push(curWidth + this.breadcrumbTipTail + ',' + (this.breadcrumbHeight / 2));
        points.push(curWidth + ',' + this.breadcrumbHeight);
        points.push('0,' + this.breadcrumbHeight);
        if (idx > 0) { // Leftmost breadcrumb; don't include 6th vertex.
            points.push(this.breadcrumbTipTail + ',' + (this.breadcrumbHeight / 2));
        }
        return points.join(' ');
    }

    public updateHover(selectedNode: ClassificationTreeNode): void {
        for (let vis of this.visualizations) {
            vis.updateHover(selectedNode);
        }
        this.updateBreadcrumbs(selectedNode);
    }

    public endHover(): void {
        for (let vis of this.visualizations) {
            vis.endHover();
        }
        this.updateBreadcrumbs(this.curNode);
    }

    // Update the breadcrumb trail to show the current sequence and percentage.
    public updateBreadcrumbs(selectedNode: ClassificationTreeNode) {
        const ancestors = Helpers.findAncestorsOfNodeByIds(this.curTrees[this.curDisplayedTreeIdx].root, selectedNode.fullKey);

        this.breadcrumbs = this.breadcrumbs
                               .data<ClassificationTreeNode>(ancestors, (val: ClassificationTreeNode, idx: number) => {
                                   return val.fullKey.slice(0, idx + 1).join('_');
                               });

        // Remove exiting nodes.
        const removedCount = this.breadcrumbs.exit().size();
        // remove the corresponding textWidths and margins form the array
        for (let i = 0; i < removedCount; i++) {
            this.breadcrumbTextMargins.pop();
            this.breadcrumbTextWidths.pop();
        }
        this.breadcrumbs.exit().remove();

        // Add breadcrumb and label for entering nodes.
        const entering = this.breadcrumbs.enter().append<SVGGElement>('g');

        const polygons = entering.append('polygon');

        const text = entering.append<SVGTextElement>('text')
                             .attr('y', this.breadcrumbHeight / 2)
                             .attr('dy', '0.35em')
                             .attr('text-anchor', 'middle');

        text.text(d => d.fullKey.length > 1 ? this.curTrees[this.curDisplayedTreeIdx].classifiers[d.classifierId].name + ': ' + d.key : d.key);

        const textWidths = text.nodes().map(x => x.getComputedTextLength());

        // build cumulative sum for translation
        for (let i = 0; i < textWidths.length; i++) {
            // push text width for polygon creation
            const widthWithPadding = textWidths[i] + 30;
            this.breadcrumbTextWidths.push(widthWithPadding);
            // calculate margin
            this.breadcrumbTextMargins.push(this.breadcrumbTextMargins[this.breadcrumbTextMargins.length - 1] + this.breadcrumbSpacing + widthWithPadding);
        }

        text.attr('x', (d, idx) => (this.breadcrumbTextWidths[idx] + this.breadcrumbTipTail) / 2);

        // set position for all nodes.
        entering.attr('transform', (d: ClassificationTreeNode, i: number) => {
            if (i === 0) {
                return 'translate(0, 0)';
            }
            return 'translate(' + this.breadcrumbTextMargins[i] + ', 0)';
        });

        // create polygons based on calculated size
        polygons.attr('points', (key: ClassificationTreeNode, idx: number) => this.breadcrumbPoints(idx));

        // Merge enter and update selections
        this.breadcrumbs = this.breadcrumbs.merge(entering);

        // update the click listeners (remove outdated ones, add new ones)
        const allText = this.breadcrumbs.select<SVGTextElement>('text');
        allText.filter<SVGTextElement>(d => d.fullKey.length >= this.curNode.fullKey.length)
               .style('cursor', 'auto')
               .on('click', null);

        allText.filter<SVGTextElement>(d => d.fullKey.length < this.curNode.fullKey.length)
               .style('cursor', 'pointer')
               .on('click', (breadcrumbNode) => this.displayNode(breadcrumbNode));


        // update the color after merging, because when drilling down the color might change, same when going up
        this.breadcrumbs.select('polygon').style('fill', d => {
            // theoretically we could also check whether "d" is an ancestor of this.selectedBreadcrumNode...
            if (d.fullKey.length <= this.curNode.fullKey.length) {
                return Constants.ROOT_COLOR;
            }
            return this.getColorForNode(d);
        });

        // calculate the hovered/selected percentage
        const percentage = (Helpers.getCurValueForNode(selectedNode, this.displayBytes) / Helpers.getValueFromTree(this.curTrees[this.curDisplayedTreeIdx], this.displayBytes) * 100);
        let percentageStr;
        if (percentage < 0.1) {
            percentageStr = '< 0.1%';
        } else {
            percentageStr = percentage.toFixed(1) + '%';
        }


        const endOfBreadcrumbs = this.breadcrumbTextMargins[this.breadcrumbTextMargins.length - 1] + this.breadcrumbTipTail + this.breadcrumbSpacing;
        // const endOfBreadcrumbs = this.breadcrumbTextMargins[this.breadcrumbTextMargins.length - 1] + this.breadcrumbSpacing + 10 + percentageStr.length * 5;

        // Now move and update the percentage at the end.
        const endlabel: d3.Selection<SVGTextElement, void, HTMLElement, any> = d3.select<SVGTextElement, void>('#trail').select('#endlabel');

        endlabel
            .attr('x', endOfBreadcrumbs)
            .attr('y', this.breadcrumbHeight / 2)
            .attr('dy', '0.35em')
            .attr('text-anchor', 'left')
            .text(percentageStr);

        const endOfBreadcrumbLine = endOfBreadcrumbs + endlabel.node()!.getComputedTextLength();
        // breadcrumb lineoffset only makes sense if positive
        const newBreadcrumbLineOffset = Math.max(endOfBreadcrumbLine - this.breadcrumbSvgWidth, 0);
        if (this.breadcrumbLineOffset !== newBreadcrumbLineOffset) {
            console.log("endOfBreadcrumbs: " + endOfBreadcrumbs);
            console.log("endOfBreadcrumbLine: " + endOfBreadcrumbLine);
            console.log("newBreadcrumbLineOffset: " + newBreadcrumbLineOffset);
            console.log("####### applying new offset!!!!");
            this.breadcrumbLineOffset = newBreadcrumbLineOffset;
            this.breadCrumbG
                .attr('transform', 'translate(' + -this.breadcrumbLineOffset + ', 0)');
        }

        // Make the breadcrumb trail visible, if it's hidden.
        d3.select('#trail')
          .style('visibility', '');
    }


    public addVisualization(vis: Visualization) {
        this.visualizations.push(vis);
    }

    public addAbsoluteMemoryChart(absMem: AbsoluteMemoryChart) {
        this.memChart = absMem;
    }

    public updateRectBorders(cells: d3.Selection<SVGGElement, HierarchyRectangularNode<ClassificationTreeNode>, SVGGElement, void>,
                             usePattern: boolean = false,
                             patternBGElement: d3.Selection<SVGRectElement, void, HTMLElement, void> | null = null,
                             elementShouldHaveFatBorder: (n: HierarchyRectangularNode<ClassificationTreeNode>) => boolean = this.GLOBAL_NODE_ONLY_SELF_HIGHLIGHT_SELECTION,
                             elementShouldBeOpaque: (n: HierarchyRectangularNode<ClassificationTreeNode>) => boolean = this.DEFAULT_GLOBAL_HIGHLIGHT_SELECTION) {
        const allRects = cells.select('rect');
        // dont add border for root
        if (this.curNode.idString !== this.getCurTree().root.idString) {
            allRects
                .style('stroke', null)
                .style('stroke-width', null)
                .style('stroke-dasharray', null)
                .style('opacity', Constants.NOT_HOVERED_OPACITY);

            allRects.filter<SVGGElement>(elementShouldHaveFatBorder)
                    .style('stroke', 'black')
                    .style('stroke-width', '6');

            allRects.filter<SVGGElement>(elementShouldBeOpaque)
                    .style('opacity', 1);

            if (usePattern) {
                allRects.attr('fill', (d: HierarchyRectangularNode<ClassificationTreeNode>) => this.getColorForNode(d.data));
                allRects.filter<SVGGElement>(this.GLOBAL_NODE_ONLY_SELF_HIGHLIGHT_SELECTION)
                        .attr('fill', 'url(#diagonalHatchRectangular)');
                patternBGElement!.attr('fill', this.getColorForNode(this.curNode));
            }
        } else {
            allRects
                .style('stroke', null)
                .style('stroke-width', null)
                .style('stroke-dasharray', null)
                .style('opacity', 1);
            if (usePattern) {
                allRects.attr('fill', (d: HierarchyRectangularNode<ClassificationTreeNode>) => this.getColorForNode(d.data));
            }
        }
    }

    // Used when the visualization type is switched
    public displayCurrentTree(): void {
        this.displayTree(this.curDisplayedTreeIdx);
    }

    public displayNextTree(): void {
        if (this.curDisplayedTreeIdx < this.curTrees.length - 1) {
            this.displayTree(this.curDisplayedTreeIdx + 1);
        }
    }

    public displayPreviousTree(): void {
        if (this.curDisplayedTreeIdx > 0) {
            this.displayTree(this.curDisplayedTreeIdx - 1);
        }
    }

    private assignColors() {
        // NOTE: in the new approach, we dont take the idString, but rather the key of the children on the first level
        // by doing this, any node, can get is color, by passing node.id[1] to the getColorFunction

        const uniqueFirstLevelChildren: ClassificationTreeNode[] = [];
        const processedChildren = new Set<string>();
        this.curTrees.forEach(tree => {
                                  if (tree.root.children != null) {
                                      tree.root.children.forEach(firstLevelChild => {
                                          if (!processedChildren.has(firstLevelChild.idString)) {
                                              uniqueFirstLevelChildren.push(firstLevelChild);
                                              processedChildren.add(firstLevelChild.idString);
                                          }
                                      });
                                  }
                              }
        );

        uniqueFirstLevelChildren.sort((a, b) => this.valueMapBasedOnSelectedSortingMode.get(b.idString)! - this.valueMapBasedOnSelectedSortingMode.get(a.idString)!);
        const uniqueFirstLevelChildrenKeys = uniqueFirstLevelChildren.map(node => node.key);

        // the red color has index 3 (at least in the current d3 version)
        // => remove that color from the color array, because it should be reserved for 'other'
        // the schemeCategory is a readonly array => first copy using slice, then remove the
        const reducedColors = d3.schemeCategory10.slice();
        this.otherNodeColor = reducedColors.splice(3, 1)[0];
        this.color = d3.scaleOrdinal(reducedColors).domain(uniqueFirstLevelChildrenKeys);
    }

    public createHierarchyAndPartitionSunburstFromTrees(): d3.HierarchyRectangularNode<ClassificationTreeNode>[] {
        const hierarchies = this.curTrees.map(x => this.createHierarchy(x.root));
        const trees = hierarchies.map(x => this.partitionSunburst(x));
        return trees;
    }

    public createHierarchyAndPartitionSunburst(data: ClassificationTreeNode): d3.HierarchyRectangularNode<ClassificationTreeNode> {
        return this.partitionSunburst(this.createHierarchy(data));
    }

    public createHierarchy(data: ClassificationTreeNode): d3.HierarchyNode<ClassificationTreeNode> {
        return d3.hierarchy<ClassificationTreeNode>(data)
            // compare with https://github.com/d3/d3-hierarchy#node_sum
            // each node gets assigned the value returned from this function that is evaluated for the node and the sum of its children
                 .each(node => (node as any).value = Helpers.getCurValueForNode(node.data, this.displayBytes))
    }

    public partition(root: HierarchyNode<ClassificationTreeNode>, sizeX: number, sizeY: number): d3.HierarchyRectangularNode<ClassificationTreeNode> {
        return d3.partition<ClassificationTreeNode>().size([sizeX, sizeY])(root);
    }

    private partitionSunburst(root: HierarchyNode<ClassificationTreeNode>): d3.HierarchyRectangularNode<ClassificationTreeNode> {
        return d3.partition<ClassificationTreeNode>()
            // NOTE: this specific size is important: 2 * Math.PI = radians in the full circular arc
            // and root.height +1 ensures that each element gets assigned height of 1 in the partition
                 .size([2 * Math.PI, root.height + 1])(root);
    }

    public getHoverBorderColor(node: ClassificationTreeNode): string {
        if (node.fullKey[1] === Constants.OTHER_STRING) {
            return 'yellow';
        }
        return 'red';
    }

    public getColorForNode(node: ClassificationTreeNode): string {
        // colors are assigned based on the key of the first Level children
        if (node.idString === 'Overall') {
            return Constants.ROOT_COLOR;
        }
        if (node.fullKey[1] === Constants.OTHER_STRING) {
            return this.otherNodeColor;
        }
        return this.color(node.fullKey[1]);
    }

    public getUniqueNumberForNode(key: string): number {
        return this.nodeKeyToUniqueNumberMapper.getUniqueNumberForNode(key);
    }

    private updateDisplayedNodeToClosestExistingOne() {
        const closestNode = Helpers.getClosestDisplayableNode(this.curNode, this.curTrees[this.curDisplayedTreeIdx].root);
        if (closestNode.idString !== this.curNode.idString) {
            AlertManager.showAlert(this.curNode.idString, closestNode.idString);
        }
        this.curNode = closestNode;
        this.updateBreadcrumbs(this.curNode);
    }

    // previously displayTreeWithCurIdx
    private displayTree(newIdx: number) {
        console.log(`display tree ${newIdx}`)

        this._curDisplayedTreeIdx = newIdx;
        this.updateDisplayedNodeToClosestExistingOne();

        for (let vis of this.visualizations) {
            if (vis.visualizationType() === this.shownVisualizationType) {
                vis.displayTree(this.curDisplayedTreeIdx, this.curNode);
            }
        }
        // Updates slides and timeline
        this.updateTimeInfo();
    }

    public displayNode(nodeToDisplay: ClassificationTreeNode): void {
        this.curNode = nodeToDisplay;
        this.updateBreadcrumbs(nodeToDisplay);
        for (let vis of this.visualizations) {
            if (vis.visualizationType() === this.shownVisualizationType) {
                vis.displayNode(nodeToDisplay);
            }
        }
    }

    private updateTimeInfo() {
        d3.select<HTMLInputElement, void>('#timeSlider').node()!.value = this.curDisplayedTreeIdx.toString();
        this.memChart.updateVisualization()
    }

    //#region Timeline
    public selectTreeOnTimeline(idx: number) {
        this.selectedTreeIndices.add(idx);
    }

    public deselectTreeOnTimeline(idx: number) {
        this.selectedTreeIndices.delete(idx);
    }

    public toggleTreeOnTimeline(idx: number) {
        if (this.selectedTreeIndices.has(idx)) {
            this.deselectTreeOnTimeline(idx);
        } else {
            this.selectTreeOnTimeline(idx);
        }
    }

    public updateTimelineData() {
        console.log("update timeline data");
        switch (this.shownVisualizationTypeInTimeline) {
            case TimelineVisualizationType.LocalSunburst:
                this.allTimelineTrees = this.createHierarchyAndPartitionSunburstFromTrees()
                                            .map((tree, idx) => new TimelineSunburst(idx,
                                                                                     this,
                                                                                     tree,
                                                                                     this.memChart.selectedTreesVizHeight,
                                                                                     true,
                                                                                     this.displayBytes,
                                                                                     this.selectedSortingMode));
                break;
            case 2:
                this.allTimelineTrees = this.createHierarchyAndPartitionSunburstFromTrees()
                                            .map((tree, idx) => new TimelineSunburst(idx,
                                                                                     this,
                                                                                     tree,
                                                                                     this.memChart.selectedTreesVizHeight,
                                                                                     false,
                                                                                     this.displayBytes,
                                                                                     this.selectedSortingMode));
                break;
            case 3:
                this.allTimelineTrees = TimelineIcicle.partition(this, this.memChart.selectedTreesVizHeight, true)
                                                      .map((tree, idx) => new TimelineIcicle(idx,
                                                                                             this,
                                                                                             tree,
                                                                                             this.memChart.selectedTreesVizHeight,
                                                                                             true,
                                                                                             this.displayBytes,
                                                                                             this.selectedSortingMode));
                break;
            case 4:
                this.allTimelineTrees = TimelineIcicle.partition(this, this.memChart.selectedTreesVizHeight, false)
                                                      .map((tree, idx) => new TimelineIcicle(idx,
                                                                                             this,
                                                                                             tree,
                                                                                             this.memChart.selectedTreesVizHeight,
                                                                                             false,
                                                                                             this.displayBytes,
                                                                                             this.selectedSortingMode));
                break;
        }
    }

    set shownTimelineVisualizationType(val: TimelineVisualizationType) {
        this.shownVisualizationTypeInTimeline = val;
        this.updateTimelineData();
    }

    get timelineTreeIndices() {
        const times = new Set<number>(this.selectedTreeIndices).add(this.curDisplayedTreeIdx);
        return Array.from(times.values()).sort((a, b) => a - b);
    }

    get selectedTimelineTrees(): TimelineIcicle[] | TimelineSunburst[] {
        return _.at(this.allTimelineTrees.slice(), this.timelineTreeIndices);
    }

    //#endregion Timeline
}
