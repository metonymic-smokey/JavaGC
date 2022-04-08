import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import * as d3 from 'd3';
import {HierarchyRectangularNode} from 'd3';
import Constants from '@/visualizations/util/Constants';
import VisualizationManager from "@/visualizations/VisualizationManager";
import {VisualizationType} from "@/dao/VisualizationType";

export default abstract class Visualization {

    protected svg!: d3.Selection<SVGSVGElement, void, HTMLElement, void>;
    protected curRoot!: HierarchyRectangularNode<ClassificationTreeNode>;

    constructor(protected manager: VisualizationManager) {
    }

    public abstract displayNode(nodeToDisplay: ClassificationTreeNode): void;

    public abstract updateHover(nodeToDisplay: ClassificationTreeNode): void;

    public abstract endHover(): void;

    public abstract displayTree(treeIdx: number, nodeToDisplay: ClassificationTreeNode): void;

    public abstract treesOrSortingChanged(): void;

    protected abstract clicked(clickedNode: ClassificationTreeNode): void;

    protected initSvgElement(domSelector: string, vizWidth: number, vizHeight: number) {
        this.svg = d3.select<HTMLDivElement, void>(domSelector)
                     .append('svg')
                     .attr('viewBox', '0 0 ' + vizWidth + ' ' + vizHeight)
                     .style('font', Constants.FONT)
                     .style('width', vizWidth)
                     .style('height', vizHeight);
    }

    public abstract visualizationType() : VisualizationType
}
