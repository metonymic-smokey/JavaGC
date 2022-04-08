import * as d3 from 'd3';
import Visualization from '@/visualizations/base/Visualization';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import VisualizationManager from '@/visualizations/VisualizationManager';
import Helpers from '@/visualizations/util/Helpers';
import Constants from '@/visualizations/util/Constants';
import BarChartRectInfo from '@/dao/BarChartRectInfo';
import {VisualizationType} from "@/dao/VisualizationType";


export default class HorizontalStackedBarChart extends Visualization {

    private readonly marginLeft = 150;
    private readonly marginRight = 30;
    private readonly marginBottom = 25;
    private readonly marginTop = 40;
    private readonly maxDisplayedSeries = Constants.MAX_CHILD_COUNT_AFTER_REDUCING + 1;
    private readonly minimumRectHeightForLabel = 50;
    private readonly maxDisplayedSeriesIndex = this.maxDisplayedSeries - 1;
    private readonly rectInfo = new Map<string, BarChartRectInfo>();
    private readonly firstLevelLabelOffsetFromAxis = 10;
    private readonly secondLevelLabelXMargin = 10;
    private readonly secondLevelLabelOffsetWithinRect = 5;
    private readonly secondLevelLabelHeight = 20;
    private curSnapshotRoot!: ClassificationTreeNode;
    private displayedNode!: ClassificationTreeNode;
    private g!: d3.Selection<SVGGElement, void, HTMLElement, void>;
    private cells!: d3.Selection<SVGGElement, ClassificationTreeNode, SVGGElement, void>;
    private xAxisSelection!: d3.Selection<SVGGElement, void, HTMLElement, void>;
    private firstLevelLabels!: d3.Selection<SVGTextElement, void, SVGElement, void>;
    private headLine!: d3.Selection<SVGTextElement, void, HTMLElement, void>;
    private headLineBackground!: d3.Selection<SVGRectElement, void, HTMLElement, void>;
    private xAxis!: d3.Axis<number | { valueOf(): number }>;

    private yScale!: d3.ScaleBand<number>;
    private xScale!: d3.ScaleLinear<number, number>;
    private widthWithoutMargins = -1; // dummy value
    private heightWithoutMargins = -1; // dummy value
    private barWidth = -1; // dummyValue
    private useLocalMaximum = true;
    private globalValueMaximum = -1; // dummyValue
    private secondLevelLabelRelativeYOffset = -1; // dummyValue
    private secondLevelAboveLabelRelativeYOffset = -1; // dummyValue
    private secondLevelAboveLabelLineYOffset = -1; // dummyValue

    constructor(private vizWidth: number, private vizHeight: number, manager: VisualizationManager) {
        super(manager);
        this.init();
    }

    private init() {
        this.curSnapshotRoot = this.manager.getCurTree().root;
        this.displayedNode = this.curSnapshotRoot;

        this.updateGlobalValueMax();

        this.widthWithoutMargins = this.vizWidth - this.marginLeft - this.marginRight;
        this.heightWithoutMargins = this.vizHeight - this.marginTop - this.marginBottom;

        super.initSvgElement('#stackedBarChart', this.vizWidth, this.vizHeight);

        this.g = this.svg.append<SVGGElement>('g')
            .attr('transform', 'translate(' + this.marginLeft + ',' + this.marginTop + ')');

        this.headLineBackground = this.svg.append<SVGRectElement>('rect')
            .attr('fill', Constants.ROOT_COLOR)
            .style('stroke', null)
            .style('stroke-width', 0)
            .attr('height', 25);
        this.headLine = this.svg.append<SVGTextElement>('text')
            .attr('class', 'barChartHeadLine');
        this.updateHeadLine();

        this.initFirstLevelLabels();

        this.yScale = d3.scaleBand<number>()
            .domain(Array.from(Array(this.maxDisplayedSeriesIndex).keys()))
            .range([0, this.heightWithoutMargins])
            .paddingInner(0.5)
            .paddingOuter(0.75);

        this.barWidth = this.yScale.bandwidth();
        this.secondLevelLabelRelativeYOffset = (this.barWidth - this.secondLevelLabelHeight / 2) / 2 + this.secondLevelLabelHeight / 2;
        this.secondLevelAboveLabelRelativeYOffset = -(this.barWidth / 2) + 2; // +2 is a fixed offset, so that the text is not too close to the above rectangles
        this.secondLevelAboveLabelLineYOffset = this.secondLevelAboveLabelRelativeYOffset + 3; // defines where the line, that leads to the label, ends

        this.xScale = d3.scaleLinear()
            .domain([0, this.getLocalValueMax()])
            .range([0, this.widthWithoutMargins,])
            .nice();

        const yAxis = d3.axisLeft(this.yScale)
            .tickFormat(() => '');

        this.g.append('g')
            .call(yAxis);

        this.xAxis = d3.axisBottom(this.xScale)
            .tickFormat((x) => Helpers.convertCurValToScaledString(x.valueOf(), this.manager.displayBytes));
        this.xAxisSelection = this.g.append('g')
            .attr('transform', 'translate(0,' + this.heightWithoutMargins + ')')
            .style('font-size', '12')
            .call(this.xAxis);

        const barContainerElement = this.g.append('g');

        const nodesThatRepresentRects = this.calculateNewRectSizedAndGetDisplayedNodes();

        this.cells = barContainerElement.selectAll<SVGGElement, ClassificationTreeNode>('g')
            .data<ClassificationTreeNode>(nodesThatRepresentRects, Helpers.nodeKeyFunction);

        this.finishDisplayAfterSettingData();

        this.updateFirstLevelLabels();

        // register click listeners
        const radioButtons = d3.selectAll<HTMLInputElement, void>('#SBCModeSelection > div > input');
        radioButtons.on('click', (datum, idx, nodes) => this.switchXAxisMode(idx, nodes));
    }

    private updateGlobalValueMax() {
        this.globalValueMaximum = Helpers.max(this.manager.curTrees.map(x => Helpers.max(x.root.children!.map(y => Helpers.getCurValueForNode(y, this.manager.displayBytes)))));
    }

    private getLocalValueMax(): number {
        return Helpers.max(this.manager.curTrees.map(x => Helpers.findMaxChildSizeOfNodeInTree(x.root, this.displayedNode.fullKey, this.manager.displayBytes)));
    }

    private updateHeadLine() {
        this.headLine.text(this.displayedNode.key);
        const length = Helpers.getWidthOfTextWithFont(this.displayedNode.key, Constants.HEADLINE_FONT);
        const headlinePos = this.widthWithoutMargins / 2 + this.marginLeft - length / 2;
        this.headLine.attr('transform', 'translate(' + headlinePos + ',20)');
        if (this.displayedNode !== this.curSnapshotRoot) {
            this.headLine.style('cursor', 'pointer')
                .on('click', () => this.clicked(this.displayedNode));
        } else {
            this.headLine.style('cursor', 'auto')
                .on('click', null);
        }

        this.headLineBackground.attr('transform', 'translate(' + (headlinePos - 5) + ',0)') // -5 for padding
            .attr('width', length + 10); // 10 padding
    }

    private updateXAxisDomain(metricChanged = false) {
        // we only need to update if using the local maximum
        if (this.useLocalMaximum || metricChanged) {
            const displayedNodeChildMaxValue = this.getLocalValueMax();
            this.xScale
                .domain([0, displayedNodeChildMaxValue])
                .nice();
            this.xAxisSelection.call(this.xAxis);
        }
        if (metricChanged && !this.useLocalMaximum) {
            this.xScale
                .domain([0, this.globalValueMaximum])
                .nice();
            this.xAxisSelection.call(this.xAxis);
        }
    }

    private switchXAxisMode(idx: number, nodes: HTMLInputElement[] | ArrayLike<HTMLInputElement>) {
        const selected = nodes[idx];
        const useLocalNew = selected.value !== 'global';
        if (useLocalNew !== this.useLocalMaximum) {
            this.useLocalMaximum = useLocalNew;
            if (useLocalNew) {
                this.updateXAxisDomain();
            } else {
                this.xScale
                    .domain([0, this.globalValueMaximum])
                    .nice();
                this.xAxisSelection.call(this.xAxis);
            }
            this.updateRectsAndSecondLevelLabelsAfterScaleUpdate();
        }
    }

    private updateRectsAndSecondLevelLabelsAfterScaleUpdate() {
        // calculate new sizes (with changed scaling
        this.calculateNewRectSizedAndGetDisplayedNodes();
        // apply new sized
        this.cells.call((updatedGs) => this.applyTransition(updatedGs));
        // update labels according to new sizes
        this.updateSecondLevelLabels();
    }

    private initFirstLevelLabels() {
        for (let i = 0; i < this.maxDisplayedSeries; i++) {
            this.g.append('text');
        }
        this.firstLevelLabels = this.g.selectAll<SVGTextElement, void>('text')
            .attr('height', 20);
    }

    private updateFirstLevelLabels() {
        const spaceForText = this.marginLeft - this.firstLevelLabelOffsetFromAxis;

        this.firstLevelLabels.each((data, i, nodes) => {
            const textElem = d3.select<SVGTextElement, void>(nodes[i]);
            if (i >= this.displayedNode.children!.length) {
                textElem.style('display', 'none');
                return;
            }
            textElem.style('display', null);

            const topEdgeOfBar = this.getYTopEdgeOfBarPos(i);
            const verticalCenterOfBar = topEdgeOfBar + this.barWidth / 2;
            const fullText = this.displayedNode.children![i].key;
            // this deletes the child elements
            // => we need to append the title each time
            textElem
                .text(fullText);
            const computedLength = Helpers.getWidthOfTextWithDefaultFont(fullText);
            if (computedLength > spaceForText) {
                textElem.style('transform', 'translate(' + -(this.firstLevelLabelOffsetFromAxis + spaceForText) + 'px, ' + verticalCenterOfBar + 'px)');
                Helpers.fitTextOfElementToSizeUsingEllipsis(textElem, spaceForText);
                textElem.append('title')
                    .text(fullText);
            } else {
                textElem.style('transform', 'translate(' + -(this.firstLevelLabelOffsetFromAxis + computedLength) + 'px, ' + verticalCenterOfBar + 'px)');
            }
        });

    }

    private getYTopEdgeOfBarPos(i: number) {
        return i === this.maxDisplayedSeriesIndex ? this.yScale(this.maxDisplayedSeriesIndex - 1)! + this.barWidth : this.yScale(i)! - this.barWidth;
    }

    private calculateNewRectSizedAndGetDisplayedNodes(): ClassificationTreeNode[] {
        this.rectInfo.clear();

        const nodesThatRepresentRects = Array<ClassificationTreeNode>();

        for (let i = 0; i < this.displayedNode.children!.length; i++) {
            const child = this.displayedNode.children![i];
            // if the node has no children, display itself instead of individual rects for its children
            const yPos = this.getYTopEdgeOfBarPos(i);

            const maxWidthValue = <number>this.xScale(Helpers.getCurValueForNode(child, this.manager.displayBytes));

            if (yPos == null) {
                console.error(i + ' is outside of the domain of yScale');
                return [];
            }
            if (child.children == null || child.children.length === 0) {
                nodesThatRepresentRects.push(child);
                this.rectInfo.set(child.idString, new BarChartRectInfo(0, yPos, maxWidthValue, this.barWidth, true));
            } else {
                const sum = Helpers.getCurValueForNode(child, this.manager.displayBytes);
                let curStart = 0;
                for (const grandChild of child.children) {
                    const curWidth = Helpers.getCurValueForNode(grandChild, this.manager.displayBytes) / sum * maxWidthValue;
                    // Note here, the opacity does not depend on the actual grandchild, but just on the child, as we can just drill-down a single level
                    // and as we are already in this branch of the if => we know that the child has children => we can pass the opacity directly
                    // of course that also means that the color only depends on the child and not the grandChild
                    nodesThatRepresentRects.push(grandChild);
                    this.rectInfo.set(grandChild.idString, new BarChartRectInfo(curStart, yPos, curWidth, this.barWidth, false));

                    curStart += curWidth;
                }
            }
        }
        return nodesThatRepresentRects;
    }


    private finishDisplayAfterSettingData() {
        const exitNodes = this.cells.exit();
        exitNodes.remove();

        const enteredSet = this.cells.enter()
            .append('g').call((newGs) => this.applyRectInfo(newGs));

        // NOTE: current version only send the parent node to the manager for hovering
        enteredSet.on('mouseover', (hoveredElement) => {
            const rectInfo = this.rectInfo.get(hoveredElement.idString)!;
            const selectedNode = rectInfo.isSoleParent ? hoveredElement : Helpers.findParentOfNodeById(this.curSnapshotRoot, hoveredElement.fullKey);
            this.manager.updateHover(selectedNode);
        })
            .on('mouseout', () => this.manager.endHover());

        this.cells.call((updatedGs) => this.applyTransition(updatedGs));

        this.cells = this.cells.merge(enteredSet);

        this.cells = this.cells.sort((a, b) => a.fullKey[a.fullKey.length - 2].localeCompare(b.fullKey[b.fullKey.length - 2]));
        this.updateSecondLevelLabels();

        // remove click listener where not needed
        this.cells.filter<SVGGElement>((d: ClassificationTreeNode) => this.rectInfo.get(d.idString)!.isSoleParent)
            .style('cursor', 'auto')
            .on('click', null);

        // we allow clicks on all siblings, even if they are not parents anymore, because
        // we will just drill down a single level anyway
        this.cells.filter<SVGGElement>((d: ClassificationTreeNode) => !this.rectInfo.get(d.idString)!.isSoleParent)
            .style('cursor', 'pointer')
            .on('click', (node) => this.clicked(node));
    }

    private updateSecondLevelLabels() {
        let oldId = '';
        let canLabelOnSide = false;
        let consecutiveChildren = 0;
        this.cells.each((d, i, nodes) => {
            const curRectInfo = this.rectInfo.get(d.idString);
            if (curRectInfo == null) {
                console.error('rect info is null in second level label generation');
                return;
            }
            const g = d3.select<SVGGElement, ClassificationTreeNode>(nodes[i]);
            const rect = g.select<SVGRectElement>('rect');


            const groupId = curRectInfo.isSoleParent ? d.idString : Helpers.getParentIdString(d);

            if (oldId !== groupId) {
                canLabelOnSide = true;
                oldId = groupId;
                consecutiveChildren = 0;
            } else {
                consecutiveChildren++;
            }
            let label = g.select<SVGTextElement>('text');

            const text = d.key;
            if (curRectInfo.isSoleParent) {
                // remove the old lines
                g.selectAll<SVGLineElement, void>('line').remove();
                if (label.size() !== 0) {
                    label.style('display', 'none');
                }
                rect.text(null);
                rect.append('title')
                    .text(text);
            } else {
                let isNewLabel = false;
                if (label.size() === 0) {
                    label = g.append<SVGTextElement>('text')
                        .style('font-size', '12px')
                        .attr('height', 16);
                    isNewLabel = true;
                }
                const isLastChildOfParent: boolean = // find parent and check that the handled element is the last element
                    this.displayedNode.children!.find(x => x.idString === Helpers.getParentIdString(d))!.children!.length - 1 === consecutiveChildren;

                canLabelOnSide = this.updateSecondLevelLabel(g, rect, label, text, curRectInfo, canLabelOnSide, isLastChildOfParent, isNewLabel);
            }
        });
    }

    // used for existing/updated
    private applyTransition(gs: d3.Selection<SVGGElement, ClassificationTreeNode, SVGGElement, void>) {
        gs.each((d, i, nodes) => {
            const curRectInfo = this.rectInfo.get(d.idString);
            if (curRectInfo == null) {
                console.error('rect info is null');
                return;
            }

            const g = d3.select(nodes[i]);

            g.transition()
                .duration(1000)
                .attr('transform', 'translate(' + curRectInfo.x + ', ' + curRectInfo.y + ')');

            const rect = g.select<SVGRectElement>('rect')
                .attr('fill', this.manager.getColorForNode(d));
            rect.transition()
                .duration(1000)
                .attr('width', curRectInfo.width);
        });
    }

    // used for new/entered rects
    private applyRectInfo(gs: d3.Selection<SVGGElement, ClassificationTreeNode, SVGGElement, void>) {
        gs.each((d, i, nodes) => {
            const curRectInfo = this.rectInfo.get(d.idString);
            if (curRectInfo == null) {
                console.error('rect info is null');
                return;
            }
            const g = d3.select<SVGGElement, ClassificationTreeNode>(nodes[i]);
            g.attr('transform', 'translate(' + curRectInfo.x + ', ' + curRectInfo.y + ')')
                .append('rect') // we could also append outside of the loop and just select here...
                .attr('width', curRectInfo.width)
                .attr('height', curRectInfo.height)
                .attr('fill', this.manager.getColorForNode(d))
                .attr('opacity', Constants.NODE_DEFAULT_OPACITY);
        });
    }

    private updateSecondLevelLabel(g: d3.Selection<SVGGElement, ClassificationTreeNode, any, any>, rect: d3.Selection<SVGRectElement, any, any, any>, label: d3.Selection<SVGTextElement, any, any, any>,
                                   text: string, rectInfo: BarChartRectInfo, canLabelAbove: boolean, isLastChildOfParent: boolean, isNewLabel: boolean): boolean {

        // like in the first level, this call deletes the child elements  (i.e. the title)
        label.text(text);
        // delete the title of the rect, if one exists
        rect.text(null);
        const rectWidth = rectInfo.width;
        // reset label visibility, otherwise we cannot calculate required length
        label.style('display', null);

        const computedLength = Helpers.getWidthOfTextWithDefaultFont(text);
        const usableWidth = rectWidth - 10;

        // remove the old lines
        g.selectAll<SVGLineElement, void>('line').remove();

        let shouldAnimate = !isNewLabel;
        let dx = (rectWidth / 2 - computedLength / 2);
        let dy = this.secondLevelLabelRelativeYOffset;

        if (computedLength < usableWidth) {
            // enough space for label
            label.style('display', null);
        } else if (canLabelAbove) {
            // put label on side
            label.style('display', null);

            dy = this.secondLevelAboveLabelRelativeYOffset;
            const usableWidthAbove = this.widthWithoutMargins - 2 * this.secondLevelLabelXMargin; // 10px margin top and bottom
            let labelCenter = -1;

            let needExtendedLine = false;
            if (computedLength > usableWidthAbove) {
                Helpers.fitTextOfElementToSizeUsingEllipsis(label, usableWidthAbove);
                const labelStart = this.secondLevelLabelXMargin;
                dx = labelStart;
                label.append('title')
                    .text(text);
                labelCenter = labelStart + computedLength / 2;
            } else {
                const absoluteOptimalCenterOfLabel = rectInfo.x + rectWidth / 2;

                if (absoluteOptimalCenterOfLabel + computedLength / 2 > this.widthWithoutMargins - this.secondLevelLabelXMargin) { // we would go outside of writable area on right side
                    labelCenter = this.widthWithoutMargins - this.secondLevelLabelXMargin - computedLength / 2 - rectInfo.x; // clamp to max translation, we have to subtract rectInfo.y to get to relative value
                    needExtendedLine = this.checkIfExtendedLiningNeeded(isLastChildOfParent, labelCenter, rectWidth);
                } else if (absoluteOptimalCenterOfLabel - computedLength / 2 - 10 < 0) { // we are too far to the left
                    labelCenter = this.secondLevelLabelXMargin + computedLength / 2 - rectInfo.x;
                    needExtendedLine = this.checkIfExtendedLiningNeeded(isLastChildOfParent, labelCenter, rectWidth);
                } else {
                    // everything fine
                    labelCenter = absoluteOptimalCenterOfLabel - rectInfo.x; // we subtract rectInfo.y, because that value is already translated by the g
                }

                dx = labelCenter - computedLength / 2;
            }
            if (needExtendedLine) {
                // line1 : from Center of Bar only change on y Axis
                const targetYLine1 = this.secondLevelAboveLabelLineYOffset / 2;
                g.append<SVGLineElement>('line')
                    .style('stroke', 'black')
                    .style('stroke-width', '2')
                    .attr('x1', rectWidth / 2)
                    .attr('y1', this.barWidth / 2)
                    .attr('x2', rectWidth / 2)
                    .attr('y2', targetYLine1);

                // line2: from end of line1 to labelCenter, targetYLine1
                g.append<SVGLineElement>('line')
                    .style('stroke', 'black')
                    .style('stroke-width', '2')
                    .attr('x1', rectWidth / 2)
                    .attr('y1', targetYLine1)
                    .attr('x2', labelCenter)
                    .attr('y2', targetYLine1);

                // line3: from end of line2 to labelCenter, this.secondLevelAboveLabelLineYOffset
                g.append<SVGLineElement>('line')
                    .style('stroke', 'black')
                    .style('stroke-width', '2')
                    .attr('x1', labelCenter)
                    .attr('y1', targetYLine1)
                    .attr('x2', labelCenter)
                    .attr('y2', this.secondLevelAboveLabelLineYOffset);

            } else {
                g.append<SVGLineElement>('line')
                    .style('stroke', 'black')  // colour the line
                    .style('stroke-width', '2')
                    // .attr('x1', rectInfo.x)     // x position of the first end of the line
                    .attr('x1', rectInfo.width / 2)     // x position of the first end of the line
                    .attr('y1', this.barWidth / 2)      // y position of the first end of the line
                    .attr('x2', labelCenter)     // x position of the second end of the line
                    .attr('y2', this.secondLevelAboveLabelLineYOffset);
            }

            // the side label space was used => return false
            canLabelAbove = false;
        } else if (rectWidth < this.minimumRectHeightForLabel) {
            // dont display text at all
            label.style('display', 'none');
            rect.append('title')
                .text(text);
            shouldAnimate = false;
        } else {
            // display part of the text
            dx = this.secondLevelLabelOffsetWithinRect;
            label.style('display', null);
            Helpers.fitTextOfElementToSizeUsingEllipsis(label, usableWidth);
            rect.append('title')
                .text(text);
        }
        if (shouldAnimate) {
            label.transition()
                .duration(Constants.TRANSITION_TIME)
                .attr('dx', dx)
                .attr('dy', dy);
        } else {
            label
                .attr('dx', dx)
                .attr('dy', dy);
        }

        return canLabelAbove;
    }

    private checkIfExtendedLiningNeeded(isLastChildOfParent: boolean, labelCenter: number, rectWidth: number,): boolean {
        if (!isLastChildOfParent) {
            // check whether we first pass the top horizontal side => ok
            // otherwise if we first pass the right vertical side => make advanced line connection

            const distY = this.barWidth / 2 - this.secondLevelAboveLabelLineYOffset;
            const distX = Math.abs(labelCenter - rectWidth / 2);
            const factor = distY / distX; // e.g. if distY = 2 and distX = 10 => 0.2 => if distance to nextRect = 4 => we go UPWARDS 4 * 0.2 before hitting next rect

            const xDistToStartOfRectCell = rectWidth / 2;
            const yUntilHittingNextRightRect = factor * xDistToStartOfRectCell;
            const lineErr = yUntilHittingNextRightRect < this.barWidth / 2;
            if (lineErr) {
                return true;
            }
        }
        return false;
    }

    public treesOrSortingChanged() {
        this.displayedNode = this.manager.getCurDisplayedNode();
        this.curSnapshotRoot = this.manager.getCurTree().root;
        // update headline - NOTE: only necessary if displayed node changed
        this.updateHeadLine();
        this.updateGlobalValueMax();
        this.updateXAxisDomain(true);

        this.updateRectsAndLabels();
    }

    private updateRectsAndLabels() {
        const nodesOfRects = this.calculateNewRectSizedAndGetDisplayedNodes();

        this.cells = this.cells.data<ClassificationTreeNode>(nodesOfRects, Helpers.nodeKeyFunction);
        this.finishDisplayAfterSettingData();
        this.updateFirstLevelLabels();
    }

    protected clicked(clickedNode: ClassificationTreeNode): void {
        this.manager.displayNode(Helpers.findParentOfNodeById(this.curSnapshotRoot, clickedNode.fullKey));
    }

    displayNode(nodeToDisplay: ClassificationTreeNode): void {
        const oldNode = this.displayedNode;
        this.displayedNode = nodeToDisplay;
        if (oldNode.idString !== this.displayedNode.idString) {
            this.updateXAxisDomain();
        }

        this.updateHeadLine();
        this.updateRectsAndLabels();
    }

    displayTree(treeIdx: number, nodeToDisplay: ClassificationTreeNode): void {
        this.curSnapshotRoot = this.manager.getCurTree().root;
        this.displayNode(nodeToDisplay);
    }

    endHover(): void {
        this.cells.style('opacity', 1);
    }

    updateHover(nodeToHighlight: ClassificationTreeNode): void {
        const rectInfo = this.rectInfo.get(nodeToHighlight.idString);
        this.cells.style('opacity', Constants.NOT_HOVERED_OPACITY);
        if (rectInfo == null) {
            // this node has no direct representation, as it is displayed using its child rects
            this.cells.filter(node => Helpers.isAncestorOrSame(node.idString, nodeToHighlight.idString)).style('opacity', 1);
        } else {
            // if we want to be able to highlight individual rects - because it would make sense regarding breadcrumbs, and consistency
            // we just need to keep the content of the then branch and remove the rest of the if statement (and theoretically can remove the isSoleParent property)
            if (rectInfo.isSoleParent) {
                // just highlight this node
                this.cells.filter(node => node.idString === nodeToHighlight.idString).style('opacity', 1);
            } else {
                // highlight the node and its siblings => just filter for all rects with same parent
                this.cells.filter(node => Helpers.isAncestorOrSame(node.idString, Helpers.getParentIdString(nodeToHighlight))).style('opacity', 1);
            }
        }
    }

    visualizationType(): VisualizationType {
        return VisualizationType.HorizontalBarChart;
    }
}
