import * as d3 from 'd3';
import {HierarchyNode, HierarchyRectangularNode} from 'd3';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import MyRect from '@/dao/MyRect';
import VisualizationManager from '@/visualizations/VisualizationManager';
import Helpers from '@/visualizations/util/Helpers';
import BaseSunburst from '@/visualizations/base/BaseSunburst';
import Constants from '@/visualizations/util/Constants';


export default class LocalSunburst extends BaseSunburst {

    protected readonly labelTexts = new Map<string, string>();
    private curSnapshotRoot!: HierarchyRectangularNode<ClassificationTreeNode>;
    private curRootInGlobalHierarchy!: HierarchyRectangularNode<ClassificationTreeNode>;
    private textContainer!: d3.Selection<SVGGElement, HierarchyRectangularNode<ClassificationTreeNode>, SVGGElement, void>;
    private lastHoveredidString!: string;

    constructor(pageWidth: number, pageHeight: number, manager: VisualizationManager) {
        super(pageWidth, pageHeight, manager, '#zoomableSunburst');
        this.levelLimit = 2;
        this.initLocal();
    }

    private initLocal() {
        this.calculateDataBasedOnTrees();
        this.curSnapshotRoot = this.partitionedTrees[0];
        this.curRootInGlobalHierarchy = this.curRoot;

        const curDisplayedNodes = Helpers.get2LevelsOfHierarchyDescendantsWithoutRoot(this.curRoot);

        this.textContainer = this.g.append('g')
                                 .selectAll<SVGGElement, HierarchyRectangularNode<ClassificationTreeNode>>('g');

        this.finishInit(curDisplayedNodes);
        this.centerCircle
            .on('click', () => this.clicked(this.curRootInGlobalHierarchy.parent != null ? this.curRootInGlobalHierarchy.parent.data : this.curRootInGlobalHierarchy.data));
    }

    protected clicked(clickedNode: ClassificationTreeNode): void {
        if (clickedNode.idString === this.lastHoveredidString) {
            this.manager.endHover();
        }
        super.clicked(clickedNode);
    }

    protected updatePathsAndLabels() {
        const nodesWithoutRoot = Helpers.get2LevelsOfHierarchyDescendantsWithoutRoot(this.curRoot);

        this.textContainer = this.textContainer
                                 .data<HierarchyRectangularNode<ClassificationTreeNode>>(nodesWithoutRoot, Helpers.hierarchyKeyFunction);

        super.updatePathsAndLabels(nodesWithoutRoot);
    }

    protected finishPathUpdateAfterSettingData() {
        super.finishPathUpdateAfterSettingData();

        this.circleSegments.filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.data.idString === this.curRootInGlobalHierarchy.data.idString)
            .on('mouseover', null);

        // NOTE: it would be enough to register the listener on the enteredSet, but as this is not exposed in the current implementation we just register them for all paths
        this.circleSegments
            .on('mouseout', () => this.manager.endHover())
            .filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.data.idString !== this.curRootInGlobalHierarchy.data.idString)
            .on('mouseover', (hoveredElement) => this.manager.updateHover(hoveredElement.data));

        // remove click listener where not needed
        this.circleSegments.filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.children == null)
            .style('cursor', 'auto')
            .on('click', null);

        // add where needed
        this.circleSegments.filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.children != null)
            .style('cursor', 'pointer')
            .on('click', (p) => this.clicked(p.data));

        // ### handle labels ###
        const textContainerExitSet = this.textContainer.exit();
        textContainerExitSet.remove();
        const enteredTextContainerSet = this.textContainer.enter()
                                            .append('g');

        enteredTextContainerSet.append('clipPath')
                               .attr('id', d => 'clip_' + this.manager.getUniqueNumberForNode(d.data.idString))
                               .append('use')
                               .attr('xlink:href', d => '#shape_' + this.manager.getUniqueNumberForNode(d.data.idString));

        const transformContainer = enteredTextContainerSet.append('g');

        transformContainer
            .attr('pointer-events', 'none')
            .style('user-select', 'none');


        const arrows = transformContainer.append('text');
        // .attr('text-anchor', 'middle')
        arrows.attr('transform', d => this.arrowTransform(new MyRect(d.x0, d.x1, d.y0, d.y1)))
              .attr('dy', '0.35em')
              .text('>');
        arrows.classed('arrow', true);

        const textContainer = transformContainer.append('g')
                                                .attr('clip-path', d => 'url(#clip_' + this.manager.getUniqueNumberForNode(d.data.idString) + ')');
        const texts = textContainer.append('text')
                                   .attr('text-anchor', 'middle')
                                   .attr('fill-opacity', d => this.labelVisibleValue(new MyRect(d.x0, d.x1, d.y0, d.y1)))
                                   .attr('transform', d => this.labelTransform(new MyRect(d.x0, d.x1, d.y0, d.y1)))
                                   .attr('dy', '0.35em')
                                   .text((d: HierarchyNode<ClassificationTreeNode>) => this.labelTexts.get(d.data.idString) || d.data.key);
        texts.classed('label', true);
        texts.each((d, i, nodes) => {
            const curNode = nodes[i];
            const usableDepth = this.radius - 20;
            Helpers.clipTextIfNecessary(d.data.idString, this.labelTexts, curNode, usableDepth);
        });

        this.textContainer = this.textContainer.merge(enteredTextContainerSet);
    }

    protected updateParentAndPerformTransition() {
        super.updateParentAndPerformTransition();

        this.textContainer.select<SVGTextElement>('.label')
            .filter<SVGTextElement>((d: HierarchyRectangularNode<ClassificationTreeNode>, index, nodes) => {
                const curNode: SVGTextElement = nodes[index];
                return curNode.hasAttribute('fill-opacity') || this.labelVisible(new MyRect(d.x0, d.x1, d.y0, d.y1));
            })
            .attr('fill-opacity', (d) => this.labelVisibleValue(new MyRect(d.x0, d.x1, d.y0, d.y1)))
            .transition()
            .duration(Constants.TRANSITION_TIME)
            // Note, this does only work, because the rect in the map is updated continuously in the attrTween of the paths
            .attrTween('transform', (d: HierarchyRectangularNode<ClassificationTreeNode>) => () => this.labelTransform(this.nodeCurRect.get(d.data.idString)!));

        this.textContainer.select<SVGTextElement>('.arrow')
            .attr('fill-opacity', (d) => +(d.y1 === 3 && d.children != null && d.children.length > 0))
            .filter<SVGTextElement>((d: HierarchyRectangularNode<ClassificationTreeNode>, index, nodes) => d.y1 === 3 && d.children != null && d.children.length > 0)
            .transition()
            .duration(Constants.TRANSITION_TIME)
            // Note, this does only work, because the rect in the map is updated continuously in the attrTween of the paths
            .attrTween('transform', (d: HierarchyRectangularNode<ClassificationTreeNode>) => () => this.arrowTransform(this.nodeCurRect.get(d.data.idString)!));
    }

    private labelVisible(rect: MyRect): boolean {
        // at least in our base case - because we chose root.height+1 as size for y for the partition
        // would also make sense, because it just depends on x (the angle in our case)
        // Old version
        // return rect.y1 <= (this.displayedLevelCount + 1) && rect.y0 >= 1 && rect.x1 - rect.x0 > 0.1;
        if (rect.y0 >= 3) {
            console.error('have to calculate relative y value');
        }

        return rect.y1 <= (this.displayedLevelCount + 1) && (rect.y0 === 1 && rect.x1 - rect.x0 > 0.2 || rect.y0 === 2 && rect.x1 - rect.x0 > 0.06);
    }

    // returns 0 if false and 1 if true
    private labelVisibleValue(rect: MyRect): number {
        // the plus operator explained for true and false
        // +false = 0
        // +true = 1
        return +this.labelVisible(rect);
    }

    private labelTransform(rect: MyRect) {
        let x;
        // this check was introduced such that labels of elements, that span the whole circle, are displayed horizontally instead of vertically
        if (Helpers.approximatelyEquals(rect.x0, 0) && Helpers.approximatelyEquals(rect.x1, Constants.TWO_PI)) {
            x = 90;
        } else {
            x = (rect.x0 + rect.x1) / 2 * 180 / Math.PI;
        }

        // NOTE: same decision as with arcGenerator - but here using this.centerRadius as min value, seems definitely better
        // const y = Math.max(((rect.y0 + rect.y1) / 2 - 1) * this.radius + this.centerRadius, 0);
        const y = Math.max(((rect.y0 + rect.y1) / 2 - 1) * this.radius + this.centerRadius, this.centerRadius);
        return `rotate(${x - 90}) translate(${y},0) rotate(${x < 180 ? 0 : 180})`;
    }

    private arrowTransform(rect: MyRect) {
        let x;
        // this check was introduced such that labels of elements, that span the whole circle, are displayed horizontally instead of vertically
        if (Helpers.approximatelyEquals(rect.x0, 0) && Helpers.approximatelyEquals(rect.x1, Constants.TWO_PI)) {
            x = 90;
        } else {
            x = (rect.x0 + rect.x1) / 2 * 180 / Math.PI;
        }

        const y = (rect.y1 - 1) * this.radius + this.centerRadius;
        return `rotate(${x - 90}) translate(${y},0) `;
    }

    displayNode(nodeToDisplay: ClassificationTreeNode): void {
        const selectedNodeInHierarchy = Helpers.findHierarchyDescendentByIds(this.curSnapshotRoot, nodeToDisplay.fullKey);

        this.displayNodeWithCurRootInGlobalHierarchy(selectedNodeInHierarchy);
    }

    private displayNodeWithCurRootInGlobalHierarchy(rootInHierarchy: HierarchyRectangularNode<ClassificationTreeNode>) {
        this.curRootInGlobalHierarchy = rootInHierarchy;
        const independentHierarchyRoot = this.manager.createHierarchyAndPartitionSunburst(rootInHierarchy.data);

        this.curRoot = independentHierarchyRoot;
        this.updatePathsAndLabels();

        this.updateParentAndPerformTransition();
    }

    displayTree(treeIdx: number, nodeToDisplay: ClassificationTreeNode): void {
        this.curSnapshotRoot = this.partitionedTrees[treeIdx];

        this.displayNode(nodeToDisplay);
    }

    updateHover(nodeToDisplay: ClassificationTreeNode): void {
        this.circleSegments.style('opacity', Constants.NOT_HOVERED_OPACITY.toString());
        // for all ancestors and the node itself set the opacity back to 1.0
        this.circleSegments.filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) =>
                                                    Helpers.isAncestorOrSame(nodeToDisplay.idString, d.data.idString))
            .style('opacity', '1');

        this.lastHoveredidString = nodeToDisplay.idString;
    }

    endHover(): void {
        this.circleSegments.style('opacity', '1');
    }
}
