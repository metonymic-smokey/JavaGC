import {HierarchyRectangularNode} from 'd3';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import VisualizationManager from '@/visualizations/VisualizationManager';
import Helpers from '@/visualizations/util/Helpers';
import Constants from '@/visualizations/util/Constants';
import BaseIcicle from '@/visualizations/base/BaseIcicle';


export default class LocalIcicle extends BaseIcicle {

    private static readonly arrowOffset = 10;
    private curRootInLocalHierarchy!: HierarchyRectangularNode<ClassificationTreeNode>;
    // used when going up one level to the parent
    private curRootInGlobalHierarchy!: HierarchyRectangularNode<ClassificationTreeNode>;

    constructor(pageWidth: number, pageHeight: number, manager: VisualizationManager) {
        super(pageWidth, pageHeight, manager, '#localIcicle', true, (pageWidth - LocalIcicle.arrowOffset) / 3);
    }

    partitionTree(treeRoot: ClassificationTreeNode) {
        const hierarchy = this.manager.createHierarchy(treeRoot);
        // ignore warning, its fine and also note that the "-10" are for the drill-down arrows
        return this.manager.partition(hierarchy, this.pageHeight, (hierarchy.height + 1) * (this.pageWidth - LocalIcicle.arrowOffset) / 3);
    }

    init() {
        super.init();
        this.curRootInLocalHierarchy = this.curRoot;
        // the method is theoretically called from super.init(), but then this.curRootInLocalHierarchy is not yet set (and this.curRoot is also only set in super.init())
        this.updateSvgElements();
    }

    protected updateRectsForCurTree(root: HierarchyRectangularNode<ClassificationTreeNode>) {
        // handle texts
        super.updateRectsForCurTree(root);
    }

    protected updateSvgElements() {
        super.updateSvgElements();

        if (this.curRootInLocalHierarchy != null) {
            const allRects = this.cells.select('rect');
            allRects.filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.data.idString === this.curRootInLocalHierarchy.data.idString)
                    .attr('fill', () => Constants.ROOT_COLOR)
                    .on('mouseover', null);

            allRects
                .on('mouseout', () => this.manager.endHover())
                .filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.data.idString !== this.curRootInLocalHierarchy.data.idString)
                .on('mouseover', (hoveredElement) => this.manager.updateHover(hoveredElement.data));

            allRects.filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.data.idString === this.curRootInLocalHierarchy.data.idString)
                    .attr('fill', () => Constants.ROOT_COLOR);
        }
    }

    protected clicked(clickedNode: ClassificationTreeNode): void {
        if (clickedNode.idString === this.curRoot.data.idString) {
            return;
        }
        const nodeToDisplay =
            clickedNode.idString === this.curRootInLocalHierarchy.data.idString
                ? this.curRootInGlobalHierarchy.parent!.data : clickedNode;

        this.manager.displayNode(nodeToDisplay);
    }

    displayNode(nodeToDisplay: ClassificationTreeNode): void {
        this.displayNodeWithHierarchyNode(Helpers.findHierarchyDescendentByIds(this.curRoot, nodeToDisplay.fullKey));
    }

    displayNodeWithHierarchyNode(nodeToDisplay: HierarchyRectangularNode<ClassificationTreeNode>) {
        this.curRootInGlobalHierarchy = nodeToDisplay;
        this.curRootInLocalHierarchy = this.partitionTree(nodeToDisplay.data);

        this.updateRectsForCurTree(this.curRootInLocalHierarchy);
    }

    displayTree(treeIdx: number, nodeToDisplay: ClassificationTreeNode): void {
        this.curRoot = this.partitionedTrees[treeIdx];

        this.displayNode(nodeToDisplay);
    }

    updateHover(nodeToDisplay: ClassificationTreeNode): void {
        const allRects = this.cells.select('rect');
        allRects.style('opacity', Constants.NOT_HOVERED_OPACITY.toString());
        // // for all ancestors, descendants and the node itself set the opacity back to 1.0
        allRects.filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) =>
                                         Helpers.isAncestorOrSame(nodeToDisplay.idString, d.data.idString))
                .style('opacity', '1');
    }

    endHover(): void {
        this.cells.select('rect')
            .style('opacity', '1');
    }

}
