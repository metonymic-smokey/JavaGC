import ClassificationTree from "@/dao/ClassificationTree";
import Helpers from "@/visualizations/util/Helpers";
import VisualizationManager from "@/visualizations/VisualizationManager";
import AbsoluteMemoryChart from "@/visualizations/timeline/AbsoluteMemoryChart";
import LocalSunburst from "@/visualizations/local/LocalSunburst";
import GlobalSunburst from "@/visualizations/global/GlobalSunburst";
import LocalIcicle from "@/visualizations/local/LocalIcicle";
import GlobalIcicle from "@/visualizations/global/GlobalIcicle";
import LocalTreeMap from "@/visualizations/local/LocalTreeMap";
import GlobalTreeMap from "@/visualizations/global/GlobalTreeMap";
import HorizontalStackedBarChart from "@/visualizations/HorizontalStackedBarChart";
import ClassificationTreeNode from "@/dao/ClassificationTreeNode";
import Constants from "@/visualizations/util/Constants";

export default class DataInitializer {
    constructor() {
    }

    // NOTE: data is expected to be read from JSON files that store heap trees
    // Thus, data is not a real ClassificationTree[] but just just an any[]
    // This method takes care of wrapping them into real ClassificationTree objects
    public initVisualizationManager(manager: VisualizationManager, data: any[]) {
        function convertTree(fakeTree: ClassificationTree): ClassificationTree {
            const convertedRoot = Helpers.convertNodeRecursively(fakeTree.root);
            return new ClassificationTree(fakeTree.classifiers, fakeTree.time, convertedRoot);
        }

        // new addition, empty trees that just contain the root node are disregarded, including them does more harm than good
        const originalTrees =
            data.filter(tree => tree.root != null && tree.root.children != null && tree.root.children.length != 0)
                .sort((tree1, tree2) => tree1.time - tree2.time)
                .map(tree => convertTree(tree));

        this.handleMissingData(originalTrees, new Map<string, number>());

        const width = window.innerWidth;
        const height = window.innerHeight;
        let maxWidth;
        if (width >= 1600) {
            maxWidth = (width) / 2;
        } else {
            maxWidth = width;
        }

        const absoluteMemoryChartHeight = 250;
        const maxHeight = (height - absoluteMemoryChartHeight - 100); // guessing around 100 for breadcrumb, separator and stuff

        maxWidth = Math.min(maxWidth, 750);
        // we want square visualizations => just use min size
        const sideSize = Math.min(maxWidth, maxHeight);

        manager.init(originalTrees);
        // it does not matter which tree array we hand into the absoluteMemoryChart, because it only uses the values of the root node anyway
        // manager.addAbsoluteMemoryChart(new AbsoluteMemoryChart(originalTrees, 200, 200, manager));
        manager.addAbsoluteMemoryChart(new AbsoluteMemoryChart(originalTrees, absoluteMemoryChartHeight, manager));
        manager.addVisualization(new LocalSunburst(sideSize, sideSize, manager));
        manager.addVisualization(new GlobalSunburst(sideSize, sideSize, manager));
        manager.addVisualization(new LocalIcicle(sideSize, sideSize, manager));
        manager.addVisualization(new GlobalIcicle(sideSize, sideSize, manager));
        manager.addVisualization(new LocalTreeMap(sideSize, sideSize, manager));
        manager.addVisualization(new GlobalTreeMap(sideSize, sideSize, manager));
        manager.addVisualization(new HorizontalStackedBarChart(Math.min(width, sideSize * 2), sideSize, manager));
        manager.lateInit();

        return manager;
    }

    private handleMissingData(trees: ClassificationTree[], sortMap: Map<string, number>) {
        function handleMissingDataRecursively(node: ClassificationTreeNode, sortMap: Map<string, number>) {
            if (node.children == null || node.children.length == 0) {
                return;
            }

            // calculate sum of children
            const byteChildValueSum = node.children.map(x => Helpers.getCurValueForNode(x, true)).reduce((a, b) => a + b);
            const objectChildValueSum = node.children.map(x => Helpers.getCurValueForNode(x, false)).reduce((a, b) => a + b);
            // calculate diff (node.val, childSum)
            const byteParentChildrenValueDiff = Helpers.getCurValueForNode(node, true) - byteChildValueSum;
            const objectParentChildrenValueDiff = Helpers.getCurValueForNode(node, false) - objectChildValueSum;

            if (byteParentChildrenValueDiff < 0) {
                console.error("byte diff must not be smaller than 0, but is " + byteParentChildrenValueDiff + " for " + node.fullKeyAsString);
                return;
            }
            if (objectParentChildrenValueDiff < 0) {
                console.error("object diff must not be smaller than 0, but is " + objectParentChildrenValueDiff + " for " + node.fullKeyAsString);
                return;
            }

            for (const child of node.children) {
                handleMissingDataRecursively(child, sortMap);
            }

            if (byteParentChildrenValueDiff == 0 && objectParentChildrenValueDiff == 0) {
                // there is no missing data
                return;
            }

            // version 1
            const otherNodeInfo = Helpers.getOrCreateOtherNodeWithInfo(node, true);
            const otherNode = otherNodeInfo[0];
            sortMap.set(otherNode.fullKeyAsString, Constants.OTHER_SORT_VALUE); // just used some high value that ensures that the other series is always displayed last

            otherNode.bytes += byteParentChildrenValueDiff;
            otherNode.objects += objectParentChildrenValueDiff;
            // if already already existed
            if (!otherNodeInfo[1]) {
                handleMissingDataRecursively(otherNode, sortMap);
            }
        }

        trees.forEach(tree => {
            handleMissingDataRecursively(tree.root, sortMap);
        });
    }
}
