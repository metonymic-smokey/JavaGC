import * as d3 from "d3";
import {HierarchyRectangularNode} from "d3";
import ClassificationTreeNode from "@/dao/ClassificationTreeNode";
import MyRect from "@/dao/MyRect";
import {Arc} from "d3-shape";
import VisualizationManager from "@/visualizations/VisualizationManager";
import Constants from "@/visualizations/util/Constants";
import Helpers from "@/visualizations/util/Helpers";

export default class TimelineSunburst {
    // Const
    protected readonly centerDiameter = 15;
    protected readonly margin = 12;
    protected readonly centerRadius = this.centerDiameter / 2;

    // Data
    private displayedLevelCount: number;
    private radiusPerLevel: number;

    constructor(public idx: number,
                public manager: VisualizationManager,
                private partitionedTree: d3.HierarchyRectangularNode<ClassificationTreeNode>,
                private pageWidth: number,
                public isLocal: boolean,
                public showBytes: boolean,
                public sortingMode: number) {
        if (isLocal) {
            this.displayedLevelCount = Math.min(2, partitionedTree.height);
        } else {
            this.displayedLevelCount = Math.min(10, partitionedTree.height);
        }
        // we subtract 5 from the pagewidth, because we want some margin on the side
        this.radiusPerLevel = (this.pageWidth - this.margin - this.centerDiameter) / (this.displayedLevelCount * 2);
    }

    public draw(svg: SVGGElement) {
        const arcGenerator: Arc<any, MyRect> = d3.arc<MyRect>()
                                                 .startAngle((d) => d.x0)
                                                 .endAngle((d) => d.x1)
                                                 .padAngle((d) => Math.min((d.x1 - d.x0) / 2, 0.005))
                                                 .padRadius(this.radiusPerLevel * 1.5)
                                                 .innerRadius((d) => Math.max((d.y0 - 1) * this.radiusPerLevel + this.centerRadius, 0))
                                                 .outerRadius((d) => Math.max((d.y0 - 1) * this.radiusPerLevel, (d.y1 - 1) * this.radiusPerLevel - 1) + this.centerRadius); // the "-1" is there, so there is a small gap between the rings
        const thiz = this;

        // Root g
        d3.select<SVGGElement, TimelineSunburst>(svg)
          .selectAll("g")
          .data([this])
          .join(function (enter) {
              const g = enter.append("g")
                             .attr('transform', 'translate(' + (thiz.pageWidth / 2) + ',' + (thiz.pageWidth / 2) + ')')
                             .classed("sunburst", true);

              // Center
              g.append<SVGCircleElement>('circle')
               .attr('r', thiz.centerRadius)
               .attr('fill', Constants.ROOT_COLOR);

              return g;
          })
            // Arc segments
          .selectAll("path")
          .data<HierarchyRectangularNode<ClassificationTreeNode>>(Helpers.getNodesWithoutRoot(this.partitionedTree))
          .join(function (enter) {
              return enter.append('path');
          })
            // .attr('id', d => 'shape_' + this.manager.getUniqueNumberForNode(d.data.idString));
          .attr('d', (d: HierarchyRectangularNode<ClassificationTreeNode>) => arcGenerator(new MyRect(d.x0, d.x1, d.y0, d.y1)))
            //.append('title');
          .attr('fill', (d: HierarchyRectangularNode<ClassificationTreeNode>) => this.manager.getColorForNode(d.data))
          .filter<SVGGElement>((d: HierarchyRectangularNode<ClassificationTreeNode>) => d.depth > this.displayedLevelCount)
          .style('display', 'none');
    }
}