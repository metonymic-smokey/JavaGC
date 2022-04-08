import {default as d3} from 'd3';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import VisualizationManager from '@/visualizations/VisualizationManager';
import BaseIcicle from '@/visualizations/base/BaseIcicle';


export default class GlobalIcicle extends BaseIcicle {

    private selectionPatternBackground!: d3.Selection<SVGRectElement, void, HTMLElement, void>;
    protected hoverRectForBorder!: d3.Selection<SVGPathElement, void, HTMLElement, void>;

    constructor(pageWidth: number, pageHeight: number, manager: VisualizationManager) {
        super(pageWidth, pageHeight, manager, '#globalIcicle', false);
    }

    protected initSvgElement() {
        super.initSvgElement();
        this.svg.append<SVGRectElement>('rect')
            .attr('width', this.pageWidth)
            .attr('height', this.pageHeight)
            .attr('fill', 'none')
            .attr('stroke', 'grey')
            .attr('stroke-dasharray', '4');

        const selectionPattern = this.svg.append<SVGPatternElement>('pattern')
                                     .attr('width', 5)
                                     .attr('height', 5)
                                     .attr('patternUnits', 'userSpaceOnUse')
                                     .attr('patternTransform', 'rotate(45)')
                                     .attr('id', 'diagonalHatchRectangular');
        this.selectionPatternBackground = selectionPattern.append<SVGRectElement>('rect')
                                                          .attr('id', 'patternBackgroundRectangular')
                                                          .attr('width', '100%')
                                                          .attr('height', '100%')
                                                          .style('stroke', 'none');
        selectionPattern.append<SVGLineElement>('line')
                        .attr('x1', 5)
                        .attr('x2', 5)
                        .attr('y2', 10)
                        .style('stroke', 'blue')
                        .style('stroke-width', 5);

        this.hoverRectForBorder = this.svg.append<SVGPathElement>('rect');
        this.hoverRectForBorder
            .style('stroke', 'red')
            .style('stroke-width', 4)
            .style('stroke-dasharray', "15 8")
            .attr('fill', 'none');
    }

    partitionTree(treeRoot: ClassificationTreeNode) {
        const hierarchy = this.manager.createHierarchy(treeRoot);

        const areaScaleFactor = this.manager.getScalingFactor(treeRoot);
        const curHeight = this.pageHeight * areaScaleFactor;

        // ignore warning, its fine
        // noinspection JSSuspiciousNameCombination
        return this.manager.partition(hierarchy, curHeight, this.pageWidth);
    }

    clicked(clickedNode: ClassificationTreeNode): void {
        this.manager.displayNode(clickedNode);
    }

    displayNode(nodeToDisplay: ClassificationTreeNode): void {
        this.updateRectsBordersIcicle();
    }


    private updateRectsBordersIcicle() {
        this.manager.updateRectBorders(this.cells, true, this.selectionPatternBackground);
    }

    displayTree(treeIdx: number, nodeToDisplay: ClassificationTreeNode): void {
        this.curRoot = this.partitionedTrees[treeIdx];

        this.updateRectWidth();

        this.updateRectsForCurTree(this.curRoot);
        this.displayNode(nodeToDisplay);
    }

    updateHover(nodeToDisplay: ClassificationTreeNode): void {
        this.updateRectsBordersIcicle();

        const selectedCell = this.cells.filter(x => x.data.idString === nodeToDisplay.idString);
        const selectedCellData = selectedCell.datum();

        const hoverColor = this.manager.getHoverBorderColor(nodeToDisplay);

        this.hoverRectForBorder
            .attr('height', () => this.rectHeight(selectedCellData))
            .attr('width', this.rectWidth)
            .attr('transform', `translate(${selectedCellData.depth * this.rectWidth},${selectedCellData.x0})`)
            .style('display', null)
            .style('stroke', hoverColor);
    }

    endHover(): void {
        this.updateRectsBordersIcicle();
        this.hoverRectForBorder.style('display', 'none');
    }

}
