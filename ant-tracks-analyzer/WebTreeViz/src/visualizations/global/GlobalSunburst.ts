import * as d3 from 'd3';
import {HierarchyRectangularNode} from 'd3';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import VisualizationManager from '@/visualizations/VisualizationManager';
import Helpers from '@/visualizations/util/Helpers';
import BaseSunburst from '@/visualizations/base/BaseSunburst';
import Constants from '@/visualizations/util/Constants';
import MyRect from "@/dao/MyRect";

export default class GlobalSunburst extends BaseSunburst {

    private displayedNode!: ClassificationTreeNode;
    private selectionPattern!: d3.Selection<SVGPatternElement, void, HTMLElement, void>;
    private selectionPatternBackground!: d3.Selection<SVGRectElement, void, HTMLElement, void>;
    protected selectedSubTreePathForBorder!: d3.Selection<SVGPathElement, void, HTMLElement, void>;
    protected hoverPathForBorder!: d3.Selection<SVGPathElement, void, HTMLElement, void>;

    constructor(pageWidth: number, pageHeight: number, manager: VisualizationManager) {
        super(pageWidth, pageHeight, manager, '#globalSunburst');
        this.globalInit();
    }

    private globalInit() {
        this.calculateDataBasedOnTrees();
        this.finishInit(Helpers.getNodesWithoutRoot(this.curRoot));

        this.centerCircle
            .on('click', () => this.clicked(this.curRoot.data));

        this.updateClickListeners();

        this.displayedNode = this.manager.getCurDisplayedNode();

        this.selectedSubTreePathForBorder = this.g.append<SVGPathElement>('path');
        this.selectedSubTreePathForBorder
            .style('stroke', 'black')
            .style('stroke-width', 6)
            .attr('fill', 'none');

        this.hoverPathForBorder = this.g.append<SVGPathElement>('path');
        this.hoverPathForBorder
            .style('stroke', 'red')
            .style('stroke-width', 4)
            .style('stroke-dasharray', "15 8")
            .attr('fill', 'none');

        this.selectionPattern = this.svg.append<SVGPatternElement>('pattern')
                                    .attr('width', 5)
                                    .attr('height', 5)
                                    .attr('patternUnits', 'userSpaceOnUse')
                                    .attr('id', 'diagonalHatch');
        this.selectionPatternBackground = this.selectionPattern.append<SVGRectElement>('rect')
                                              .attr('id', 'patternBackground')
                                              .attr('width', '100%')
                                              .attr('height', '100%');
        this.selectionPattern.append<SVGLineElement>('line')
            .attr('x1', 5)
            .attr('x2', 5)
            .attr('y2', 10)
            .style('stroke', 'blue')
            .style('stroke-width', 5);
    }

    displayNode(nodeToDisplay: ClassificationTreeNode): void {
        this.displayedNode = nodeToDisplay;
        this.setOpacityAndBorder();
    }

    private setOpacityAndBorder() {
        // handle the case when we are drilled down and thus want to highlight something
        if (this.displayedNode.idString !== this.curRoot.data.idString) {
            this.circleSegments
                .style('stroke', null)
                .style('stroke-width', null)
                .style('opacity', Constants.NOT_HOVERED_OPACITY)
                .attr('fill', (d: HierarchyRectangularNode<ClassificationTreeNode>) => this.manager.getColorForNode(d.data));

            const targetNodeSelection = this.circleSegments
                                            .filter((d) => Helpers.isDescendantOrSame(this.displayedNode.idString, d.data.idString) || Helpers.isAncestorOrSame(this.displayedNode.idString,
                                                                                                                                                                d.data.idString))
                                            .style('opacity', 1)
                                            .filter((d) => this.displayedNode.idString === d.data.idString);

            const targetHierarchyNode = targetNodeSelection.datum();
            const averageRot = targetHierarchyNode.x0 + (targetHierarchyNode.x1 - targetHierarchyNode.x0) / 2;

            this.selectionPattern
                .attr('patternTransform', 'rotate(' + (Helpers.toDegrees(averageRot) + 45) + ')');
            this.selectionPatternBackground.attr('fill', this.manager.getColorForNode(this.displayedNode));


            targetNodeSelection
                .attr('fill', 'url(#diagonalHatch)');
            if (this.arcVisible(new MyRect(targetHierarchyNode.x0, targetHierarchyNode.x1, targetHierarchyNode.y0, targetHierarchyNode.y1))) {

                this.selectedSubTreePathForBorder
                    .style('display', null)
                    .transition()
                    .duration(Constants.TRANSITION_TIME)
                    .attrTween('d', () => this.tweenArc(targetHierarchyNode));
            } else {
                this.selectedSubTreePathForBorder.style('display', 'none');
            }
        } else {
            this.selectedSubTreePathForBorder.style('display', 'none');

            this.circleSegments
                .style('stroke', null)
                .style('stroke-width', null)
                .style('opacity', 1)
                .attr('fill', (d: HierarchyRectangularNode<ClassificationTreeNode>) => this.manager.getColorForNode(d.data));

        }
    }

    displayTree(treeIdx: number, nodeToDisplay: ClassificationTreeNode): void {
        this.curRoot = this.partitionedTrees[treeIdx];
        this.displayedNode = nodeToDisplay;

        this.displayedLevelCount = this.calculateDisplayedLevelCount();
        this.targetRadius = this.calculateRadius();

        this.updatePathsAndLabels(Helpers.getNodesWithoutRoot(this.curRoot));

        this.updateParentAndPerformTransition();

        this.updateClickListeners();

        this.setOpacityAndBorder();
    }

    updateClickListeners() {
        // remove click listener where not needed
        this.circleSegments.filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.children == null)
            .style('cursor', 'auto')
            .on('click', null);
        // add where needed
        this.circleSegments.filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.children != null)
            .style('cursor', 'pointer')
            .on('click', (p) => this.clicked(p.data));
    }

    updateHover(nodeToDisplay: ClassificationTreeNode): void {
        const selectedPath =
            this.circleSegments.filter(d => d.data.idString === nodeToDisplay.idString);

        const selectedHierarchyNode = selectedPath.datum();
        if (this.arcVisible(new MyRect(selectedHierarchyNode.x0, selectedHierarchyNode.x1, selectedHierarchyNode.y0, selectedHierarchyNode.y1))) {
            const hoverColor = this.manager.getHoverBorderColor(selectedHierarchyNode.data);

            const selectedPathRect = new MyRect(selectedHierarchyNode.x0, selectedHierarchyNode.x1, selectedHierarchyNode.y0, selectedHierarchyNode.y1);
            this.hoverPathForBorder
                .style('display', null)
                .style('stroke', hoverColor)
                .attr('d', this.arcGenerator(selectedPathRect)!);
        }

    }

    endHover(): void {
        this.setOpacityAndBorder();
        this.hoverPathForBorder.style('display', 'none');
    }

    protected calculateDisplayedLevelCount(): number {
        return Math.min(this.curRoot.height, Constants.GLOBAL_LEVEL_LIMIT);
    }

}
