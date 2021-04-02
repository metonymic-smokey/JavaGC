import Visualization from '@/visualizations/base/Visualization';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import VisualizationManager from '@/visualizations/VisualizationManager';
import Constants from '@/visualizations/util/Constants';
import * as d3 from 'd3';
import {HierarchyRectangularNode} from 'd3';
import Helpers from '@/visualizations/util/Helpers';
import {VisualizationType} from "@/dao/VisualizationType";


export default abstract class BaseTreeMap extends Visualization {

    // these two trees do not change based on node selection/timestep,...
    private readonly PADDING_OUTER = 5;
    private readonly horizontalLabelMargin = 4;
    private readonly minimumRectWidthForLabel = 40;
    private readonly minimumRectHeightForLabel = 25;
    protected g!: d3.Selection<SVGGElement, void, HTMLElement, void>;
    protected cells!: d3.Selection<SVGGElement, HierarchyRectangularNode<ClassificationTreeNode>, SVGGElement, void>;

    constructor(protected vizWidth: number, protected vizHeight: number, manager: VisualizationManager, private isLocal: boolean, protected domParentSelector: string, private readonly PADDING_TOP = 5) {
        super(manager);
        this.init();
    }

    protected init(): void {
        super.initSvgElement(this.domParentSelector, this.vizWidth, this.vizHeight);

        this.g = this.svg.append('g');

        this.cells = this.g
                         .selectAll<SVGGElement, HierarchyRectangularNode<ClassificationTreeNode>>('g');

        this.displayNode(this.manager.getCurDisplayedNode());
    }

    protected performTreemap(treemapRoot: ClassificationTreeNode): HierarchyRectangularNode<ClassificationTreeNode> {
        const hierarchy = this.manager.createHierarchy(treemapRoot);

        let curWidth = this.vizWidth;
        let curHeight = this.vizHeight;
        if (!this.isLocal) {
            const areaFactor = this.manager.getScalingFactor(treemapRoot);
            const sideFactor = Math.sqrt(areaFactor);
            curWidth *= sideFactor;
            curHeight *= sideFactor;

            const offsetX = (this.vizWidth - curWidth) / 2;
            const offsetY = (this.vizWidth - curHeight) / 2;

            this.g.attr('transform', 'translate(' + offsetX + ',' + offsetY + ')');
        }

        return d3.treemap<ClassificationTreeNode>()
                 .size([curWidth, curHeight])
                 .paddingInner(3)
                 .paddingOuter(this.PADDING_OUTER)
                 .paddingTop(this.PADDING_TOP) // for labeling?
                 (hierarchy);
    }

    protected finishDisplayAfterSettingData() {
        this.cells.exit().remove();

        const enteredSet = this.cells.enter()
                               .append('g');

        enteredSet.append('title');

        enteredSet.append('rect')
                  .attr('fill-opacity', Constants.NODE_DEFAULT_OPACITY);

        if (this.isLocal) {
            const enteredTexts = enteredSet.append('text')
                                           .style('user-select', 'none')
                                           .attr('pointer-events', 'none')
                                           .attr('x', this.horizontalLabelMargin)
                                           .attr('y', 16);

            enteredTexts.append('tspan');
        }

        // merge cells
        this.cells = this.cells.merge(enteredSet);
        this.cells
            .transition()
            .duration(Constants.TRANSITION_TIME)
            .attr('transform', d => `translate(${d.x0},${d.y0})`);

        // update rects
        const allRects = this.cells.select('rect');

        allRects
            // update the color for all, even though it would be enough to update for the node that was previously selected (but with the current setup we dont have that info)
            .attr('fill', d => this.manager.getColorForNode(d.data))
            .transition()
            .duration(Constants.TRANSITION_TIME)
            .attr('height', d => d.y1 - d.y0)
            .attr('width', d => d.x1 - d.x0);

        // update click listeners
        allRects.filter<SVGTextElement>(d => d.children == null)
                .style('cursor', 'auto')
                .on('click', null);

        allRects.filter<SVGTextElement>(d => d.children != null)
                .style('cursor', 'pointer')
                .on('click', (p) => this.clicked(p.data));

        if (this.isLocal) {
            this.updateTexts();
        }
    }

    private updateTexts() {
        this.cells.each((d, i, nodes) => {
            const rectWidth = d.x1 - d.x0;
            const rectHeight = d.y1 - d.y0;
            const curG = d3.select<SVGGElement, ClassificationTreeNode>(nodes[i]);
            const curTitle = curG.select<SVGTitleElement>('title');
            const curLabel = curG.select<SVGTextElement>('text');
            if (rectWidth < this.minimumRectWidthForLabel || rectHeight < this.minimumRectHeightForLabel) {
                curLabel.style('display', 'none');
                curTitle.text(d.data.key);
                return;
            }

            curLabel.text(d.data.key);
            // reset label visibility, otherwise we cannot calculate required length
            curLabel.style('display', null);
            const usableWidth = rectWidth - 2 * this.horizontalLabelMargin;
            Helpers.fitTextOfElementToSizeUsingEllipsis(curLabel, usableWidth);
        });
    }

    protected clicked(clickedNode: ClassificationTreeNode): void {
        const newRoot = clickedNode.idString === this.curRoot.data.idString ? Helpers.findParentOfNodeById(this.manager.getCurTree().root, clickedNode.fullKey) : clickedNode;

        this.manager.displayNode(newRoot);
    }

    visualizationType(): VisualizationType {
        return VisualizationType.Treemap;
    }
}
