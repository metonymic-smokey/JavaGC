import Classifier from '@/dao/Classifier';
import ClassificationTreeNode from '@/dao/ClassificationTreeNode';

export default class ClassificationTree {
    constructor(public classifiers: Classifier[], public time: number, public root: ClassificationTreeNode) {
    }

    public clone(): ClassificationTree {
        return new ClassificationTree(this.classifiers, this.time, this.root.clone());
    }
}
