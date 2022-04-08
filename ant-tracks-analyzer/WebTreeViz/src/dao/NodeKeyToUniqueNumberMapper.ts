import ClassificationTree from '@/dao/ClassificationTree';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';

// Some visualizations need a unique number for a node
export default class NodeKeyToUniqueNumberMapper {
    private curNum = 0;
    private readonly idToUniqueNumber = new Map<string, number>();

    constructor() {
    }

    public getUniqueNumberForNode(fullKey: string): number {
        const data = this.idToUniqueNumber.get(fullKey);
        if (data == null) {
            console.error('Trying to get unique number for ' + fullKey + ' but did not find the corresponding entry!');
            return undefined!;
        }
        return data;
    }

    public createUniqueNumberForEachNodeForEachTree(trees: ClassificationTree[]) {
        trees.forEach(tree => {
            this.createUniqueNumberForEachNode(tree);
        });
    }

    public createUniqueNumberForEachNode(tree: ClassificationTree) {
        if (this.idToUniqueNumber.get(tree.root.idString) == null) {
            this.idToUniqueNumber.set(tree.root.idString, this.curNum++);
        }
        this.assignUniqueNumberRecursively(tree.root);
    }

    private assignUniqueNumberRecursively(node: ClassificationTreeNode) {
        if (node.children) {
            for (let i = 0; i < node.children.length; i++) {
                const curNode = node.children[i];
                // assign a unique number
                if (this.idToUniqueNumber.get(curNode.idString) == null) {
                    this.idToUniqueNumber.set(curNode.idString, this.curNum++);
                }
                if (curNode.children != null && curNode.children.length !== 0) {
                    this.assignUniqueNumberRecursively(curNode);
                }
            }
        }
    }
}
