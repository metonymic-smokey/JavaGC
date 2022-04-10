import ClassificationTreeNode from "./ClassificationTreeNode";

export default class ReduceResult {
    constructor(public selectedKeys: Set<string>, // bad naming, please call it something else, the user "selects" things, we "keep" things, or "nonOtherKeys" or something like that
                public reducedRootNode: ClassificationTreeNode) {
    }
}