import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import VisualizationManager from '@/visualizations/VisualizationManager';
import Constants from '@/visualizations/util/Constants';
import {HierarchyRectangularNode} from 'd3';
import Helpers from '@/visualizations/util/Helpers';
import BaseTreeMap from '@/visualizations/base/BaseTreeMap';


export default class LocalTreeMap extends BaseTreeMap {

    constructor(visWidth: number, visHeight: number, manager: VisualizationManager) {
        super(visWidth, visHeight, manager, true, '#localTreemap', 24);
    }

    protected finishDisplayAfterSettingData() {
        super.finishDisplayAfterSettingData();

        const allRects = this.cells.select('rect');
        // update hover listeners
        allRects.filter<SVGGElement>((d) => d.data.idString === this.curRoot.data.idString)
                .attr('fill', () => Constants.ROOT_COLOR)
                .on('mouseover', null);

        allRects
            .on('mouseout', () => this.manager.endHover())
            .filter<SVGGElement>((d) => d.data.idString !== this.curRoot.data.idString)
            .on('mouseover', (hoveredElement) => this.manager.updateHover(hoveredElement.data));
    }

    treesOrSortingChanged(): void {
        this.displayNode(this.manager.getCurDisplayedNode());
    }

    displayNode(nodeToDisplay: ClassificationTreeNode): void {
        this.curRoot = this.performTreemap(nodeToDisplay);

        const descendants = Helpers.get2LevelsOfHierarchyDescendantsWithRoot(this.curRoot);

        this.cells = this.cells
                         .data<HierarchyRectangularNode<ClassificationTreeNode>>(descendants, Helpers.hierarchyKeyFunction);

        this.finishDisplayAfterSettingData();
    }

    displayTree(treeIdx: number, nodeToDisplay: ClassificationTreeNode): void {
        this.displayNode(nodeToDisplay);
    }

    endHover(): void {
        this.cells.select('rect')
            .style('opacity', '1');
    }

    updateHover(nodeToDisplay: ClassificationTreeNode): void {
        const allRects = this.cells.select('rect');
        allRects.style('opacity', Constants.NOT_HOVERED_OPACITY.toString());
        allRects.filter<SVGGElement>((d) =>
                                         nodeToDisplay.idString === d.data.idString)
                .style('opacity', '1');
    }

}
