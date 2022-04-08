import * as d3 from "d3";
import {HierarchyRectangularNode} from "d3";
import ClassificationTreeNode from "@/dao/ClassificationTreeNode";
import VisualizationManager from "@/visualizations/VisualizationManager";
import Helpers from "@/visualizations/util/Helpers";
import Constants from "@/visualizations/util/Constants";

export default class TimelineIcicle {
    // Const
    public static readonly margin = 12;

    // Data
    private displayedLevelCount: number;
    private rectWidth: number;

    constructor(public idx: number,
                public manager: VisualizationManager,
                private partitionedTree: d3.HierarchyRectangularNode<ClassificationTreeNode>,
                private pageWidth: number,
                public isLocal: boolean,
                public showBytes : boolean,
                public sortingMode : number) {
        if (isLocal) {
            this.displayedLevelCount = Math.min(2, partitionedTree.height);
        } else {
            this.displayedLevelCount = Math.min(10, partitionedTree.height);
        }
        this.rectWidth = partitionedTree.y1 - partitionedTree.y0;
    }

    public draw(svg: SVGGElement) {
        const thiz = this;

        // Root g
        d3.select<SVGGElement, TimelineIcicle>(svg)
          .selectAll("g")
          .data([this])
          .join(function (enter) {
              const g = enter.append("g")
                             .attr("transform", `translate(${TimelineIcicle.margin / 2}, ${TimelineIcicle.margin / 2})`)
                             .classed("icicle", true);

              if(!thiz.isLocal) {
                  // global border
                  g.append('rect')
                   .attr('width', thiz.pageWidth - TimelineIcicle.margin)
                   .attr('height', thiz.pageWidth - TimelineIcicle.margin)
                   .attr('fill', 'white')
                   .attr('stroke', 'grey')
                   .attr('stroke-dasharray', '4');
              }

              // root
              g.append('rect')
               .attr('width', thiz.rectWidth)
               .attr('fill-opacity', Constants.NODE_DEFAULT_OPACITY)
               .attr('fill', Constants.ROOT_COLOR)
               .attr('height', thiz.rectHeight(thiz.partitionedTree));


              return g;
          })
            // Arc segments
          .selectAll(".nonRootRec")
          .data<HierarchyRectangularNode<ClassificationTreeNode>>(Helpers.getNodesWithoutRoot(this.partitionedTree))
          .join(function (enter) {
              return enter.append('rect');
          })
          .attr("transform", d => `translate(${d.depth * this.rectWidth},${d.x0})`)
          .attr('fill', d => this.manager.getColorForNode(d.data))
          .attr('height', d => this.rectHeight(d))
          .attr('width', this.rectWidth)
          .classed("nonRootRec", true);
    }

    private rectHeight(d: HierarchyRectangularNode<ClassificationTreeNode>) {
        // Subtract some padding
        return d.x1 - d.x0 - Math.min(1, (d.x1 - d.x0) / 2);
    }

    public static partition(manager: VisualizationManager, height: number, isLocal: boolean) {
        return manager.curTrees.map(function (tree) {
            const scaling = isLocal ? 1 : manager.getScalingFactor(tree.root);
            return manager.partition(manager.createHierarchy(tree.root),
                                     (height - TimelineIcicle.margin) * scaling,
                                     (height - TimelineIcicle.margin));
        });
    }
}