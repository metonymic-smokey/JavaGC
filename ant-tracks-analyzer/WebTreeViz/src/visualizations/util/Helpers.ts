import * as d3 from 'd3';
import {HierarchyNode, HierarchyRectangularNode} from 'd3';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';
import ClassificationTree from '@/dao/ClassificationTree';
import Constants from '@/visualizations/util/Constants';
import {SortingMode} from '@/dao/SortingMode';
import ReduceResult from "@/dao/ReduceResult";


export default class Helpers {

    private static readonly TOLERANCE = 0.000001;
    private static readonly BILLION = 1_000_000_000;
    private static readonly MILLION = 1_000_000;
    private static readonly THOUSAND = 1_000;
    private static canvasContext: CanvasRenderingContext2D;

    public static setCanvasContext(context: CanvasRenderingContext2D) {
        Helpers.canvasContext = context;
    }

    public static getWidthOfTextWithDefaultFont(text: string): number {
        Helpers.setFontIfNotSetAlready(Constants.FONT);
        return Helpers.canvasContext.measureText(text).width;
    }

    public static getWidthOfTextWithFont(text: string, font: string): number {
        Helpers.setFontIfNotSetAlready(font);
        return Helpers.canvasContext.measureText(text).width;
    }

    private static setFontIfNotSetAlready(font: string) {
        if (Helpers.canvasContext.font !== font) {
            Helpers.canvasContext.font = font;
        }
    }

    public static getCurValueForNode(node: ClassificationTreeNode, useBytes: boolean): number {
        return useBytes ? node.bytes : node.objects;
    }

    public static getCurAxisLabel(useBytes: boolean): string {
        return useBytes ? 'Bytes' : 'Objects';
    }

    public static getValueFromTree(tree: ClassificationTree, useBytes: boolean): number {
        return this.getCurValueForNode(tree.root, useBytes);
    }

    public static convertCurValToScaledString(val: number, areBytes: boolean): string {
        let resultVal: number;
        let appendix: string;
        if (val >= this.BILLION) {
            resultVal = val / this.BILLION;
            if (areBytes) {
                return resultVal + 'GB';
            }
            return resultVal + 'B';
        } else if (val >= this.MILLION) {
            resultVal = val / this.MILLION;
            appendix = 'M';
        } else if (val >= this.THOUSAND) {
            resultVal = val / this.THOUSAND;
            appendix = 'K';
        } else {
            resultVal = val;
            appendix = '';
        }
        if (areBytes) {
            appendix += 'B';
        }
        return resultVal + ' ' + appendix;
    }

    public static mergeTreeIntoOtherNode(from: ClassificationTreeNode, to: ClassificationTreeNode, valueMap: Map<string, number> | null = null, level: number = 0) {
        if (from.children != null) {
            if (to.children == null) {
                to.children = [];
            }
            for (const fromChild of from.children) {
                let toChild = to.children.find(x => x.key === fromChild.key);
                if (toChild == null) {
                    const copiedId = [...fromChild.fullKey];
                    copiedId[copiedId.length - 2 - level] = Constants.OTHER_STRING;
                    toChild = new ClassificationTreeNode(copiedId, copiedId.toString(), fromChild.key, fromChild.classifierId, 0, 0, null);
                    to.children.push(toChild);
                }
                toChild.objects += fromChild.objects;
                toChild.bytes += fromChild.bytes;

                if (valueMap != null) {
                    const valueOfToChild = valueMap.get(toChild.idString);
                    if (valueOfToChild != null) {
                        valueMap.set(toChild.idString, valueOfToChild + valueMap.get(fromChild.idString)!);
                    } else {
                        valueMap.set(toChild.idString, valueMap.get(fromChild.idString)!);
                    }
                }

                Helpers.mergeTreeIntoOtherNode(fromChild, toChild, valueMap, level + 1);
            }
        }
    }

    public static getSortingModeForValue(val: number): SortingMode {
        if (val === 1) {
            return SortingMode.AbsoluteGrowth;
        } else if (val === 2) {
            return SortingMode.StartSize;
        } else if (val === 3) {
            return SortingMode.EndSize;
        } else {
            console.error('sorting mode not defined!');
            return null!;
        }
    }

    public static getTitleText(d: HierarchyNode<ClassificationTreeNode>): string {
        return d.ancestors()
            .map((n) => n.data.key)
            .reverse()
            .join('/') + '\n' + d3.format(',d')(d.value!);
    }

    public static findDescendentByIds(root: ClassificationTreeNode, ids: string[]): ClassificationTreeNode {
        let targetNode = root;
        // i is initialized to 1, because the first key is 'Overall' and we start by comparing with the children of root and not root itself
        for (let i = 1; i < ids.length; i++) {
            if (targetNode == null || targetNode.children == null) {
                console.log('specified invalid descendent ids - cannot find descendent of ' + root.idString + ' with ids ' + ids.join('_'));
                return undefined!;
            }
            targetNode = targetNode.children.find(x => x.key === ids[i])!;
        }
        if (targetNode == null) {
            console.log('specified invalid descendent ids - cannot find descendent of ' + root.idString + ' with ids ' + ids.join('_'));
            return undefined!;
        }
        return targetNode;
    }

    public static findHierarchyDescendentByIds(root: HierarchyRectangularNode<ClassificationTreeNode>, ids: string[]): HierarchyRectangularNode<ClassificationTreeNode> {
        let targetNode: HierarchyRectangularNode<ClassificationTreeNode> = root;
        // i is initialized to 1, because the first key is 'Overall' and we start by comparing with the children of root and not root itself
        for (let i = 1; i < ids.length; i++) {
            if (targetNode == null || targetNode.children == null) {
                console.log('specified invalid descendent ids - cannot find descendent of ' + root.data.idString + ' with ids ' + ids.join('_'));
                return undefined!;
            }
            targetNode = targetNode.children.find(x => x.data.key === ids[i])!;
        }
        if (targetNode == null) {
            console.log('specified invalid descendent ids - cannot find descendent of ' + root.data.idString + ' with ids ' + ids.join('_'));
            return undefined!;
        }
        return targetNode;
    }

    public static findAncestorsOfNodeByIds(root: ClassificationTreeNode, ids: string[]): ClassificationTreeNode[] {
        const result: ClassificationTreeNode[] = [root];

        // i is initialized to 1, because the first key is 'Overall' and we start by comparing with the children of root and not root itself
        for (let i = 1; i < ids.length; i++) {
            const node = result[i - 1];
            if (node.children != null) { // should always be true, but makes the compiler happy
                // search for the next key in the children of the previous node
                const child = node.children.find(x => x.key === ids[i]);
                if (child == null) {
                    console.log('error while looking for acestors: could not find child with key ' + ids[i] + ' of parent ' + result[i].key + ' for idString ' + ids.join('_'));
                    return undefined!;
                }
                result.push(child);
            }
        }
        return result;
    }

    public static idsEqual(idA: string[], idB: string[]) {
        if (idA.length !== idB.length) {
            return false;
        }
        for (let i = 0; i < idA.length; i++) {
            if (idA[i] !== idB[i]) {
                return false;
            }
        }
        return true;
    }

    public static calculateSizeAtSpecificTree(root: ClassificationTreeNode, useBytes: boolean) {
        function calculateSizeAtSpecificTreeRecursively(node: ClassificationTreeNode, useBytes: boolean, map: Map<string, number>) {
            for (let i = 0; i < node.children!.length; i++) {
                const curNode = node.children![i];
                map.set(curNode.idString, Helpers.getCurValueForNode(curNode, useBytes));
                if (curNode.children != null && curNode.children.length !== 0) {
                    calculateSizeAtSpecificTreeRecursively(curNode, useBytes, map);
                }
            }
        }

        const result = new Map<string, number>();
        calculateSizeAtSpecificTreeRecursively(root, useBytes, result);
        return result;
    }

    public static reduceTreeBasedOnSelectedNodeSetRecursively(node: ClassificationTreeNode, useBytes: boolean, selectedNodes: Set<string>, valueMap: Map<string, number>): ClassificationTreeNode {
        if (node.children != null && node.children.length !== 0) {
            const convertedChildren: ClassificationTreeNode[] = [];

            let otherNode = null;

            for (const child of node.children) {
                if (selectedNodes.has(child.idString)) {
                    // add to converted children
                    convertedChildren.push(this.reduceTreeBasedOnSelectedNodeSetRecursively(child, useBytes, selectedNodes, valueMap));
                } else {
                    if (otherNode == null) {
                        otherNode = Helpers.getOrCreateOtherNode(node);
                    }
                    if (child.key !== Constants.OTHER_STRING) {
                        Helpers.mergeTreeIntoOtherNode(child, otherNode);
                        otherNode.objects += child.objects;
                        otherNode.bytes += child.bytes;
                    }
                }
            }

            if (otherNode != null) {
                this.fixMissingDataOfNewlyCreatedOtherNode(otherNode);
                otherNode = this.reduceTreeBasedOnSelectedNodeSetRecursively(otherNode, useBytes, selectedNodes, valueMap);

                // otherNode has not been added yet
                // in this case, it could have been added by "being selected"s
                if (this.doesNotContainOtherNode(convertedChildren)) {
                    convertedChildren.push(otherNode);
                }

                // set "artificial" low value for other node, such that it is sorted to the end
                // we set the value to -1, such that other is even after nodes that e.g.
                // did not exist in the start, if we are using start size sorting
                if (!valueMap.has(otherNode.idString)) {
                    valueMap.set(otherNode.idString, -1);
                }
            }
            convertedChildren.sort((a, b) => this.getValueOrDefault(valueMap, b.idString, 0) - this.getValueOrDefault(valueMap, a.idString, 0));

            node.children = convertedChildren;
        }

        return node;
    }

    private static getValueOrDefault<S, T>(map: Map<S, T>, key: S, defaultVal: T): T {
        const val = map.get(key);
        if (val == null) {
            return defaultVal;
        }
        return val;
    }

    public static doesNotContainOtherNode(children: ClassificationTreeNode[]): boolean {
        return children.find(x => x.key === Constants.OTHER_STRING) == null;
    }

    public static getOrCreateOtherNode(parent: ClassificationTreeNode, addToParent: boolean = false): ClassificationTreeNode {
        return this.getOrCreateOtherNodeWithInfo(parent, addToParent)[0];
    }

    // returns also whether the otherNode hat to be created
    public static getOrCreateOtherNodeWithInfo(node: ClassificationTreeNode, addToParent: boolean = false): [ClassificationTreeNode, boolean] {
        if (node.children != null) { // should always be true, but makes the compiler happy
            let otherNode = node.children.find(x => x.key === Constants.OTHER_STRING);
            if (otherNode == null) {
                let copiedArray = node.fullKey.slice();
                copiedArray.push(Constants.OTHER_STRING);
                otherNode = new ClassificationTreeNode(copiedArray, Constants.OTHER_STRING, Constants.OTHER_STRING, node.children[0].classifierId, 0, 0, null);
                if (addToParent) {
                    node.children.push(otherNode);
                }
                return [otherNode, true];
            } else {
                if (otherNode.children != null && otherNode.children.length !== 0) {
                    console.error("other node should not have children!");
                }
            }
            return [otherNode, false];
        }
        // just needed to make the compiler happy
        return [node, false];
    }

    // This method creates the "Other" nodes
    public static reduceTree(node: ClassificationTreeNode,
                             useBytes: boolean,
                             valueMap: Map<string, number>): ReduceResult {
        const thiz = this;

        // This method creates the "Other" nodes and fills selectedNodes
        function reduceTreeRec(node: ClassificationTreeNode,
                               useBytes: boolean,
                               valueMap: Map<string, number>, // input
                               selectedNodes: Set<string>): ClassificationTreeNode {
            if (node.children != null && node.children.length !== 0) {
                // sort children by metric

                node.children.sort((a, b) => valueMap.get(b.idString)! - valueMap.get(a.idString)!);

                const overallValue = Helpers.getCurValueForNode(node, useBytes);
                let selectedValue = 0;
                let nodeCount = 0;

                const convertedChildren: ClassificationTreeNode[] = [];
                // instead of using this offset, it could also be fixed when sorting..
                let otherOffset = 0;

                // the third check is needed because it can happen that the Other node has more than 10% and then none of the other checks would be enough to make sure no error occurs
                for (; selectedValue <= overallValue * 0.9 && nodeCount < Constants.MAX_CHILD_COUNT_AFTER_REDUCING && nodeCount + otherOffset < node.children.length; nodeCount++) {
                    const curNode = node.children[nodeCount + otherOffset];
                    if (curNode.key === Constants.OTHER_STRING) {
                        // skip other node - we dont want to 'select' the 'other' node
                        otherOffset = 1;
                        nodeCount--;
                        continue;
                    }
                    convertedChildren.push(reduceTreeRec(curNode, useBytes, valueMap, selectedNodes));
                    selectedValue += Helpers.getCurValueForNode(curNode, useBytes);
                    selectedNodes.add(convertedChildren[nodeCount].idString);
                }

                if (nodeCount < node.children.length) {
                    let otherNode = Helpers.getOrCreateOtherNode(node);
                    for (; (nodeCount + otherOffset) < node.children.length; nodeCount++) {
                        const deletedNode = node.children[nodeCount + otherOffset];
                        // Dont merge already existing other node into itself
                        if (deletedNode.key !== Constants.OTHER_STRING) {
                            Helpers.mergeTreeIntoOtherNode(deletedNode, otherNode, valueMap);
                            otherNode.objects += deletedNode.objects;
                            otherNode.bytes += deletedNode.bytes;
                        }
                    }

                    thiz.fixMissingDataOfNewlyCreatedOtherNodeSingleMetric(otherNode, useBytes);

                    otherNode = reduceTreeRec(otherNode, useBytes, valueMap, selectedNodes);
                    if (thiz.doesNotContainOtherNode(convertedChildren)) {
                        convertedChildren.push(otherNode);
                    }
                }
                node.children = convertedChildren;
            }
            return node;
        }

        const selectedNodes = new Set<string>();
        const reducedTree = reduceTreeRec(node, useBytes, valueMap, selectedNodes);
        return new ReduceResult(selectedNodes, reducedTree);
    }


    public static fixMissingDataOfNewlyCreatedOtherNodeSingleMetric(otherNode: ClassificationTreeNode, useBytes: boolean) {
        this.fixMissingDataOfNewlyCreatedOtherNode(otherNode, useBytes, !useBytes);
    }

    public static fixMissingDataOfNewlyCreatedOtherNode(otherNode: ClassificationTreeNode, updateBytes: boolean = true, updateObjects: boolean = true) {
        if (otherNode.children != null && otherNode.children.length !== 0) {
            // calculate childSum
            const childByteSum = Helpers.sum(otherNode.children.map(x => x.bytes));
            const childObjectSum = Helpers.sum(otherNode.children.map(x => x.objects));

            const byteDiff = otherNode.bytes - childByteSum;
            const objectDiff = otherNode.objects - childObjectSum;

            if (byteDiff !== 0 || objectDiff !== 0) {
                const otherNode_otherChild = Helpers.getOrCreateOtherNode(otherNode, true);
                if (updateBytes) {
                    otherNode_otherChild.bytes += byteDiff;
                }
                if (updateObjects) {
                    otherNode_otherChild.objects += objectDiff;
                }
            }
        }
    }

    public static calculateAbsGrowthPerNodeRecursive(startParent: ClassificationTreeNode, endParent: ClassificationTreeNode, useBytes: boolean, changePerKey: Map<string, number>, alreadyProcessed: Set<string> = new Set<string>()) {
        if (startParent != null && startParent.children != null) {
            for (const child of startParent.children) {
                //we multiply it by -1, because if the key is not present in the last tree, it should be a negative value and we dont have to perform further steps
                changePerKey.set(child.idString, -1 * Helpers.getCurValueForNode(child, useBytes));
                // recursive call
                if (child.children != null && child.children.length != 0) {
                    this.calculateAbsGrowthPerNodeRecursive(child,
                        endParent != null && endParent.children != null ? endParent.children.find(x => Helpers.idsEqual(x.fullKey, child.fullKey))! : null!,
                        useBytes,
                        changePerKey,
                        alreadyProcessed);
                    alreadyProcessed.add(child.idString);
                }
            }
        }

        if (endParent != null && endParent.children != null) {
            for (const child of endParent.children) {
                if (!changePerKey.has(child.idString)) {
                    changePerKey.set(child.idString, Helpers.getCurValueForNode(child, useBytes));
                } else {
                    const startValue = changePerKey.get(child.idString);

                    //we ADD the startValue to the end value as the start value is negative
                    const diff = Helpers.getCurValueForNode(child, useBytes) + startValue!!;
                    changePerKey.set(child.idString, diff);
                }
                // recursive call
                if (!alreadyProcessed.has(child.idString)) {
                    if (child.children != null && child.children.length != 0) {
                        this.calculateAbsGrowthPerNodeRecursive(startParent != null && startParent.children != null ? startParent.children.find(x => Helpers.idsEqual(x.fullKey,
                            child.fullKey))! : null!,
                            child,
                            useBytes,
                            changePerKey,
                            alreadyProcessed);
                    }
                }
            }
        }
    }

    public static convertNodeRecursively(node: ClassificationTreeNode): ClassificationTreeNode {
        let clonedChildren: ClassificationTreeNode[] = null!;
        if (node.children != null) {
            clonedChildren = [];
            for (const child of node.children) {
                clonedChildren.push(Helpers.convertNodeRecursively(child));
            }
        }
        return new ClassificationTreeNode(node.fullKey, node.idString, node.key, node.classifierId, node.objects, node.bytes, clonedChildren);
    }

    public static cloneTrees(originalTrees: ClassificationTree[]): ClassificationTree[] {
        return originalTrees.map(tree => tree.clone());
    }

    public static createIdString(id: string[]): string {
        return id.join('_');
    }

    public static findParentOfNodeById(root: ClassificationTreeNode, id: string[]): ClassificationTreeNode {
        let cur: ClassificationTreeNode = root;
        for (let i = 1; i < id.length - 1; i++) { // -1 because we are looking for the parent

            // search for the next key in the children of the previous node
            if (cur.children != null) { // should always be true, but makes the compiler happy
                const child = cur.children.find(x => x.key === id[i]);
                if (child == null) {
                    console.log('error while looking for parent: could not find child with key ' + id[i] + ' of parent ' + cur.key + ' for idString ' + Helpers.createIdString(id));
                    return undefined!;
                }
                cur = child;
            }
        }
        return cur;
    }

    public static findMaxChildSizeOfNodeInTree(root: ClassificationTreeNode, id: string[], useBytes: boolean) {
        let cur: ClassificationTreeNode = root;
        for (let i = 1; i < id.length; i++) { // -1 because we are looking for the parent

            // search for the next key in the children of the previous node
            const child = cur.children!.find(x => x.key === id[i]);
            if (child == null) {
                return 0;
            }
            cur = child;
        }
        return Helpers.max(cur.children!.map(y => Helpers.getCurValueForNode(y, useBytes)));
    }

    public static hierarchyKeyFunction(node: HierarchyRectangularNode<ClassificationTreeNode> | void): string {
        return node == null ? '' : node.data.idString;
    }

    public static nodeKeyFunction(node: ClassificationTreeNode): string {
        return node == null ? '' : node.idString;
    }

    public static getParentIdString(node: ClassificationTreeNode): string {
        return this.getPartialIdString(node.fullKey, node.fullKey.length - 1);
    }

    public static getPartialIdString(id: string[], keysToTake: number): string {
        return id.slice(0, keysToTake).join('_');
    }

    public static clipTextIfNecessary(idString: string, labelTexts: Map<string, string>, curNode: SVGTextElement, length: number) {
        if (!labelTexts.has(idString)) {
            const clippedText = Helpers.clipTextToSize(curNode, length);
            labelTexts.set(idString, clippedText);
        }
    }

    public static clipTextToSize(textElem: SVGTextElement, usableSize: number): string {
        const length = Helpers.getWidthOfTextWithDefaultFont(textElem.textContent!);
        if (length < usableSize) {
            return textElem.textContent!;
        }

        const textSelection = d3.select<SVGTextElement, void>(textElem);
        return Helpers.fitTextOfElementToSizeUsingEllipsis(textSelection, usableSize);
    }

    public static fitTextOfElementToSizeUsingEllipsis(element: d3.Selection<SVGTextElement, any, any, any>, widthLimit: number): string {
        let textLength2 = Helpers.getWidthOfTextWithDefaultFont(element.text());
        let text = element.text();
        while (textLength2 > widthLimit && text.length > 0) {
            text = text.slice(0, -1);
            element.text(text + '...');
            textLength2 = Helpers.getWidthOfTextWithDefaultFont(element.text());
        }
        return text + '...';
    }

    public static getClosestDisplayableNodeOld(oldSelectedNode: ClassificationTreeNode, newRoot: ClassificationTreeNode): ClassificationTreeNode {
        let closestNode = newRoot;
        let parent = newRoot;
        for (let i = 1; i < oldSelectedNode.fullKey.length; i++) { // start at 1 as we are searching for a key in root(=overall)`s children already => if we dont find => return root
            if (closestNode.children == null || closestNode.children.length === 0) {
                break;
            }
            const found = closestNode.children.find((node) => node.key === oldSelectedNode.fullKey[i]);
            // we update the parent here, because here we know that the closestNode hasChildren and thus can be displayed
            // even if we dont find the actual node we searched for
            parent = closestNode;
            if (found == null || found.children == null) { // we are only allowed to 'select/drilldown' into nodes that actually have children
                break;
            }
            closestNode = found;
        }
        if (closestNode.idString === oldSelectedNode.idString) {
            // found searched node
            if (closestNode.children != null && closestNode.children.length !== 0) {
                return closestNode;
            }
        }
        return parent;
    }

    public static getClosestDisplayableNode(oldSelectedNode: ClassificationTreeNode, newRoot: ClassificationTreeNode): ClassificationTreeNode {
        if (newRoot.children == null || newRoot.children.length == 0) { // 1
            return newRoot;
        }
        let closestNode = newRoot; // 2
        for (let searchLevel = 1; searchLevel < oldSelectedNode.fullKey.length; searchLevel++) { // 3
            if (closestNode.children != null) { // should always be true, but makes the compiler happy
                const potentialChild = closestNode.children.find((node) => node.key === oldSelectedNode.fullKey[searchLevel]); // 4
                if (potentialChild == null || potentialChild.children == null || potentialChild.children.length == 0) { // 5
                    break;
                }
                closestNode = potentialChild; // 6
            }
        }
        return closestNode; // 7
    }

    public static getClosestDisplayableNode2(oldSelectedNode: ClassificationTreeNode, newRoot: ClassificationTreeNode): ClassificationTreeNode {
        let closestNode = newRoot;
        let parent = newRoot;
        for (let searchLevel = 1; searchLevel < oldSelectedNode.fullKey.length; searchLevel++) {
            if (closestNode.children == null || closestNode.children.length === 0) {
                break;
            }
            const found = closestNode.children.find((node) => node.key === oldSelectedNode.fullKey[searchLevel]);
            parent = closestNode;
            if (found == null || found.children == null) {
                break;
            }
            closestNode = found;
        }
        if (closestNode.idString === oldSelectedNode.idString) {
            if (closestNode.children != null && closestNode.children.length !== 0) {
                return closestNode;
            }
        }
        return parent;
    }

    public static getNodesWithoutRoot(root: HierarchyRectangularNode<ClassificationTreeNode>): Array<HierarchyRectangularNode<ClassificationTreeNode>> {
        return root.descendants().slice(1);
    }

    public static get2LevelsOfHierarchyDescendantsWithoutRoot(root: HierarchyRectangularNode<ClassificationTreeNode>): Array<HierarchyRectangularNode<ClassificationTreeNode>> {
        return this.get2LevelsOfDescendantsWithInitialValues(root, []);
    }

    public static get2LevelsOfHierarchyDescendantsWithRoot(root: HierarchyRectangularNode<ClassificationTreeNode>): Array<HierarchyRectangularNode<ClassificationTreeNode>> {
        return this.get2LevelsOfDescendantsWithInitialValues(root, [root]);
    }

    private static get2LevelsOfDescendantsWithInitialValues(root: HierarchyRectangularNode<ClassificationTreeNode>, descendants: Array<HierarchyRectangularNode<ClassificationTreeNode>>): Array<HierarchyRectangularNode<ClassificationTreeNode>> {
        if (root.children != null) {
            for (let node of root.children) {
                descendants.push(node);
                if (node.children != null) {
                    node.children.forEach(child => descendants.push(child));
                }
            }
        }
        return descendants;
    }

    public static isDescendantOrSame(nodeId: string, potentialDescendantId: string): boolean {
        return nodeId === potentialDescendantId || potentialDescendantId.startsWith(nodeId + '_');
    }

    public static isAncestorOrSame(nodeId: string, potentialAncestorId: string): boolean {
        return this.isDescendantOrSame(potentialAncestorId, nodeId);
    }

    public static toDegrees(angle: number) {
        return angle * (180 / Math.PI);
    }

    public static max(vals: number[]): number {
        let max = -1;
        for (let i = 0; i < vals.length; i++) {
            if (vals[i] > max) {
                max = vals[i];
            }
        }
        return max;
    }

    public static min(vals: number[]): number {
        let max = Number.MAX_VALUE;
        for (let i = 0; i < vals.length; i++) {
            if (vals[i] < max) {
                max = vals[i];
            }
        }
        return max;
    }

    public static sum(vals: number[]): number {
        let sum = 0;
        for (const val of vals) {
            sum += val;
        }
        return sum;
    }

    public static approximatelyEquals(a: number, b: number) {
        return Math.abs(a - b) < Helpers.TOLERANCE;
    }

    private constructor() {
    }

}
