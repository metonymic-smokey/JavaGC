import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import VisualizationManager from '@/visualizations/VisualizationManager';
import {HierarchyRectangularNode} from 'd3';
import Helpers from '@/visualizations/util/Helpers';
import BaseTreeMap from '@/visualizations/base/BaseTreeMap';


export default class GlobalTreeMap extends BaseTreeMap {

    constructor(visWidth: number, visHeight: number, manager: VisualizationManager) {
        super(visWidth, visHeight, manager, false, '#globalTreemap');
    }

    protected init() {
        super.init();

        this.svg.append<SVGRectElement>('rect')
            .attr('width', this.vizWidth)
            .attr('height', this.vizHeight)
            .attr('fill', 'none')
            .attr('stroke', 'grey')
            .attr('stroke-dasharray', '4');

        this.updateThisVisualization(this.manager.getCurTree().root);
    }

    treesOrSortingChanged(): void {
        this.displayTree(this.manager.curDisplayedTreeIdx, this.manager.getCurDisplayedNode());
    }

    private updateThisVisualization(rootToDisplay: ClassificationTreeNode) {
        this.curRoot = this.performTreemap(rootToDisplay);

        const descendants = this.curRoot.descendants();

        this.cells = this.cells
                         .data<HierarchyRectangularNode<ClassificationTreeNode>>(descendants, Helpers.hierarchyKeyFunction);

        this.finishDisplayAfterSettingData();
    }

    private updateRectBordersTreemap() {
        this.manager.updateRectBorders(this.cells);
    }

    displayNode(nodeToDisplay: ClassificationTreeNode): void {
        this.updateRectBordersTreemap();
    }

    displayTree(treeIdx: number, nodeToDisplay: ClassificationTreeNode): void {
        this.updateThisVisualization(this.manager.getCurTree().root);
        this.displayNode(nodeToDisplay);
    }

    endHover(): void {
        this.updateRectBordersTreemap();
    }

    private calculateArea(node: HierarchyRectangularNode<ClassificationTreeNode>): number {
        return (node.x1 - node.x0) * (node.y1 - node.y0);
    }

    updateHover(nodeToDisplay: ClassificationTreeNode): void {
        this.updateRectBordersTreemap();

        const selectedCell = this.cells.filter(x => x.data.idString === nodeToDisplay.idString);

        const area = this.calculateArea(selectedCell.datum());

        const hoverColor = this.manager.getHoverBorderColor(nodeToDisplay);
        let dashMode = "15 8";
        if (area < 1000) {
            dashMode = "8 4";
        }

        selectedCell.select('rect')
                    .style('stroke-width', 4)
                    .style('stroke-dasharray', dashMode)
                    .style('stroke', hoverColor);
    }

}
