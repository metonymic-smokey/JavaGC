import * as d3 from 'd3';
import {HierarchyNode, HierarchyRectangularNode, Selection} from 'd3';
import Visualization from '@/visualizations/base/Visualization';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import VisualizationManager from '@/visualizations/VisualizationManager';
import Helpers from '@/visualizations/util/Helpers';
import Constants from '@/visualizations/util/Constants';
import {VisualizationType} from "@/dao/VisualizationType";


export default abstract class BaseIcicle extends Visualization {

    protected readonly labelTexts = new Map<string, string>();
    protected g!: d3.Selection<SVGGElement, void, HTMLElement, void>;
    protected partitionedTrees!: Array<HierarchyRectangularNode<ClassificationTreeNode>>;
    protected cells!: Selection<SVGGElement, HierarchyRectangularNode<ClassificationTreeNode>, SVGGElement, void>;
    private readonly LABEL_MARGIN_LEFT = 4;
    private readonly VERTICAL_TEXT_OFFSET = 16;
    protected rectWidth!: number;

    protected constructor(protected pageWidth: number, protected pageHeight: number, manager: VisualizationManager, protected domParentSelector: string, private isLocal: boolean,
                          protected customRectWidth?: number) {
        super(manager);
        this.initSvgElement();
        this.init();
    }

    abstract partitionTree(treeRoot: ClassificationTreeNode): HierarchyRectangularNode<ClassificationTreeNode>;

    protected initSvgElement() {
        super.initSvgElement(this.domParentSelector, this.pageWidth, this.pageHeight);

        this.g = this.svg.append('g');

        this.cells = this.g
                         .selectAll<SVGGElement, HierarchyRectangularNode<ClassificationTreeNode>>('g');
    }

    private partitionTrees() {
        this.partitionedTrees = this.manager.curTrees.map(x => this.partitionTree(x.root));
    }

    protected init() {
        this.partitionTrees();
        this.curRoot = this.partitionedTrees[this.manager.curDisplayedTreeIdx];

        this.updateRectWidth();

        this.updateRectsForCurTree(this.curRoot);
    }

    protected updateRectWidth() {
        if (this.customRectWidth != null) {
            this.rectWidth = this.customRectWidth;
        } else {
            this.rectWidth = this.curRoot.y1 - this.curRoot.y0;
        }
    }

    protected updateRectsForCurTree(parent: HierarchyRectangularNode<ClassificationTreeNode>) {
        const shownDescendants = this.isLocal ? Helpers.get2LevelsOfHierarchyDescendantsWithRoot(parent) : parent.descendants();
        this.cells = this.cells.data<HierarchyRectangularNode<ClassificationTreeNode>>(shownDescendants, Helpers.hierarchyKeyFunction);

        this.updateSvgElements();
    }

    protected updateSvgElements() {
        this.cells.exit().remove();

        const enteredSet = this.cells.enter()
                               .append('g');

        enteredSet.append('title');

        enteredSet.append('rect')
                  .attr('width', this.rectWidth)
                  .attr('fill-opacity', Constants.NODE_DEFAULT_OPACITY);

        if (this.isLocal) {
            enteredSet.append('text')
                      .style('user-select', 'none')
                      .attr('pointer-events', 'none')
                      .attr('x', this.LABEL_MARGIN_LEFT)
                      .attr('y', this.VERTICAL_TEXT_OFFSET)
                      .classed('normalText', true);

            const arrows = enteredSet.append('text')
                                     .style('user-select', 'none')
                                     .attr('pointer-events', 'none')
                                     .attr('y', this.VERTICAL_TEXT_OFFSET)
                                     .attr('x', this.rectWidth);
            arrows.text('>');
            arrows.classed('arrow', true);
        }

        // merge cells
        this.cells = this.cells.merge(enteredSet);
        this.cells
            .transition()
            .duration(Constants.TRANSITION_TIME)
            .attrTween('transform', (d, i, nodes) => {
                const curNode = nodes[i];
                let curTransform = curNode.getAttribute('transform');
                const targetTransform = `translate(${d.depth * this.rectWidth},${d.x0})`;
                if (curTransform == null) {
                    curTransform = targetTransform;
                }
                return d3.interpolate(curTransform, targetTransform);
            });

        // update rects
        const allRects = this.cells.select('rect');

        allRects
            // update the color for all, even though it would be enough to update for the node that was previously selected (but with the current setup we dont have that info)
            .attr('fill', d => this.manager.getColorForNode(d.data))
            .transition()
            .duration(Constants.TRANSITION_TIME)
            .attr('height', d => this.rectHeight(d))
            .attr('width', this.rectWidth);

        // update click listeners
        allRects.filter<SVGTextElement>(d => d.children == null)
                .style('cursor', 'auto')
                .on('click', null);

        allRects.filter<SVGTextElement>(d => d.children != null)
                .style('cursor', 'pointer')
                .on('click', (p) => this.clicked(p.data));

        if (this.isLocal) {
            // update texts
            const allNormalTexts = this.cells.select<SVGTextElement>('.normalText');
            allNormalTexts.attr('fill-opacity', d => this.labelVisibleValue(d));
            allNormalTexts
                .text((d: HierarchyNode<ClassificationTreeNode>) => this.labelTexts.get(d.data.idString) || d.data.key);
            allNormalTexts.each((d, i, nodes) => {
                const curNode = nodes[i];
                const usableSize = this.rectWidth - this.LABEL_MARGIN_LEFT * 2;
                Helpers.clipTextIfNecessary(d.data.idString, this.labelTexts, curNode, usableSize);
            });

            const allArrows = this.cells.select<SVGTextElement>('.arrow');
            allArrows
                .attr('fill-opacity', (d) => +(d.depth === 2 && d.children != null && d.children.length > 0))
                .transition()
                .duration(Constants.TRANSITION_TIME)
                .attr('dy', d => (this.rectHeight(d) - this.VERTICAL_TEXT_OFFSET) / 2);
        }
        // update titles
        this.cells.select('title')
            .text(d => Helpers.getTitleText(d));
    }

    protected rectHeight(d: HierarchyRectangularNode<ClassificationTreeNode>) {
        return d.x1 - d.x0 - Math.min(1, (d.x1 - d.x0) / 2);
    }

    protected labelVisible(d: HierarchyRectangularNode<ClassificationTreeNode>) {
        return d.y1 <= this.pageWidth && d.y0 >= 0 && d.x1 - d.x0 > 18;
    }

    // returns 0 if false and 1 if true
    protected labelVisibleValue(d: HierarchyRectangularNode<ClassificationTreeNode>): number {
        // the plus operator explained for true and false
        // +false = 0
        // +true = 1
        return +this.labelVisible(d);
    }

    public treesOrSortingChanged(): void {
        this.partitionTrees();
        this.displayTree(this.manager.curDisplayedTreeIdx, this.manager.getCurDisplayedNode());
    }

    visualizationType(): VisualizationType {
        return VisualizationType.Icicle;
    }
}
