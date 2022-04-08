import Visualization from '@/visualizations/base/Visualization';
import MyRect from '@/dao/MyRect';
import {Arc} from 'd3-shape';
import * as d3 from 'd3';
import {HierarchyNode, HierarchyRectangularNode} from 'd3';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import VisualizationManager from '@/visualizations/VisualizationManager';
import Helpers from '@/visualizations/util/Helpers';
import Constants from '@/visualizations/util/Constants';
import {VisualizationType} from "@/dao/VisualizationType";

export default abstract class BaseSunburst extends Visualization {

    protected readonly centerDiameter = 50;
    protected readonly centerRadius = this.centerDiameter / 2;
    protected readonly nodeCurRect = new Map<string, MyRect>();

    protected partitionedTrees!: Array<HierarchyRectangularNode<ClassificationTreeNode>>;
    protected displayedLevelCount!: number;
    protected levelLimit: number = Constants.GLOBAL_LEVEL_LIMIT;
    protected radius!: number;
    protected targetRadius!: number;
    protected g!: d3.Selection<SVGGElement, void, HTMLElement, void>;
    protected centerCircle!: d3.Selection<SVGCircleElement, void, HTMLElement, void>;
    protected circleSegments!: d3.Selection<SVGPathElement, HierarchyRectangularNode<ClassificationTreeNode>, SVGGElement, void>;

    // returns an arc generator - i.e. an implementation of the interface that can be called
    // see e.g. https://stackoverflow.com/questions/30675519/implement-function-without-name-in-typescript
    protected arcGenerator: Arc<any, MyRect> = d3.arc<MyRect>()
                                                 .startAngle((d) => d.x0)
                                                 .endAngle((d) => d.x1)
                                                 .padAngle((d) => Math.min((d.x1 - d.x0) / 2, 0.005))
                                                 .padRadius(this.radius * 1.5)
                                                 .innerRadius((d) => Math.max((d.y0 - 1) * this.radius + this.centerRadius, 0))
                                                 .outerRadius((d) => Math.max((d.y0 - 1) * this.radius, (d.y1 - 1) * this.radius - 1) + this.centerRadius); // the "-1" is there, so there is a small gap between the rings


    protected constructor(protected pageWidth: number, protected pageHeight: number, manager: VisualizationManager, protected domParentSelector: string) {
        super(manager);
        this.initSvgNodes();
    }

    protected initSvgNodes() {
        super.initSvgElement(this.domParentSelector, this.pageWidth, this.pageHeight);

        const halfWidth = this.pageWidth / 2;
        this.g = this.svg.append('g')
                     .attr('transform', 'translate(' + halfWidth + ',' + halfWidth + ')');

        this.circleSegments = this.g.append('g')
                                  .selectAll<SVGPathElement, HierarchyRectangularNode<ClassificationTreeNode>>('path');
    }

    protected calculateDataBasedOnTrees() {
        this.partitionedTrees = this.manager.createHierarchyAndPartitionSunburstFromTrees();
        this.curRoot = this.partitionedTrees[this.manager.curDisplayedTreeIdx];
        this.displayedLevelCount = this.calculateDisplayedLevelCount();
        this.radius = this.calculateRadius();
    }

    protected calculateDisplayedLevelCount(): number {
        const heights = this.partitionedTrees.map<number>(x => x.height);
        const maxHeight = Helpers.max(heights);

        // limit the count of levels to display
        return Math.min(this.levelLimit, maxHeight);
    }

    protected calculateRadius(): number {
        // we subtract 15 from the pagewidth, because we want some margin for the drill-down arrows
        // (this margin is also added for the global version, but this is no problem)
        return (this.pageWidth - 15 - this.centerDiameter) / (this.displayedLevelCount * 2);
    }

    protected finishInit(nodes: HierarchyRectangularNode<ClassificationTreeNode>[]) {
        this.updatePathsAndLabels(nodes);

        // initialize the central Circle that is used to go one level "up"
        this.centerCircle = this.g.append<SVGCircleElement>('circle')
                                .attr('r', this.centerRadius)
                                .attr('fill', Constants.ROOT_COLOR)
                                .attr('pointer-events', 'all');

        this.updateParentAndPerformTransition();
    }

    protected updatePathsAndLabels(nodes: HierarchyRectangularNode<ClassificationTreeNode>[]) {
        this.circleSegments = this.circleSegments
                                  .data<HierarchyRectangularNode<ClassificationTreeNode>>(nodes, Helpers.hierarchyKeyFunction);

        this.finishPathUpdateAfterSettingData();
    }

    protected finishPathUpdateAfterSettingData() {
        this.circleSegments
            .exit<HierarchyRectangularNode<ClassificationTreeNode>>()
            .remove();

        // gather newly inserted paths
        const enteredSet = this.circleSegments
                               .enter()
                               .append('path')
                               .attr('id', d => 'shape_' + this.manager.getUniqueNumberForNode(d.data.idString));

        // calculate their initial sizes
        enteredSet.data()
                  .forEach(node => {
                      this.nodeCurRect.set(node.data.idString, new MyRect(node.x0, node.x1, node.y0, node.y0));
                  });

        // set their remaining properties
        enteredSet.attr('d', (d: HierarchyRectangularNode<ClassificationTreeNode>) => this.arcGenerator(new MyRect(d.x0, d.x1, d.y0, d.y1))).append('title');


        // merge the updated paths with the newly created paths
        this.circleSegments = this.circleSegments.merge(enteredSet);
        // update the title - necessary for both NEW and UPDATED ones
        this.circleSegments
            .attr('fill', (d: HierarchyRectangularNode<ClassificationTreeNode>) => this.manager.getColorForNode(d.data))
            .select<SVGTitleElement>('title')
            .text((d: HierarchyNode<ClassificationTreeNode>) => d.ancestors()
                                                                 .map((n) => n.data.key)
                                                                 .reverse()
                                                                 .join('/') + '\n' + d3.format(',d')(d.value!)
            );

        this.circleSegments
            .filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.depth > this.displayedLevelCount)
            .style('display', 'none');
        this.circleSegments
            .filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.depth <= this.displayedLevelCount)
            .style('display', null);
    }

    protected updateParentAndPerformTransition() {
        this.circleSegments
            .attr('fill-opacity', (d: HierarchyRectangularNode<ClassificationTreeNode>) => this.arcVisible(new MyRect(d.x0, d.x1, d.y0, d.y1)) ? Constants.NODE_DEFAULT_OPACITY : 0)
            .transition()
            .duration(Constants.TRANSITION_TIME)
            .attrTween('d', (d: HierarchyRectangularNode<ClassificationTreeNode>) => this.tweenArc(d));
    }

    protected tweenArc(d: HierarchyRectangularNode<ClassificationTreeNode>) {
        let curRect = this.nodeCurRect.get(d.data.idString);
        const targetRect = new MyRect(d.x0, d.x1, d.y0, d.y1);
        const interpolator = d3.interpolate(curRect, targetRect);
        const radiusDiff = this.targetRadius - this.radius;
        const needsRadiusInterpol = this.targetRadius != null && this.targetRadius !== this.radius;
        const oldRadius = this.radius;
        return (i: number) => {
            // NOTE: when global displayed count changes, that affects the arc generator
            // thus there is no interpolation...
            // obvious solution: also interpolate radius
            const interpolated = interpolator(i);
            if (needsRadiusInterpol) {
                this.radius = oldRadius + i * radiusDiff;
            }

            this.nodeCurRect.set(d.data.idString, interpolated);
            return this.arcGenerator(interpolated)!;
        };
    }

    protected arcVisible(rect: MyRect) {
        // y is checked, because only levels 1-3 are visible,
        // x is checked, because when an a certain node is displayed/drilled-down => the other nodes that are not displayed have x0 = x1
        // => this code part returns false
        return rect.y1 <= (this.displayedLevelCount + 1) && rect.y0 >= 1 && rect.x1 > rect.x0;
    }

    protected clicked(clickedNode: ClassificationTreeNode): void {
        this.manager.displayNode(clickedNode);
    }

    public treesOrSortingChanged(): void {
        this.calculateDataBasedOnTrees();

        this.displayTree(this.manager.curDisplayedTreeIdx, this.manager.getCurDisplayedNode());
    }

    visualizationType(): VisualizationType {
        return VisualizationType.Sunburst;
    }
}
