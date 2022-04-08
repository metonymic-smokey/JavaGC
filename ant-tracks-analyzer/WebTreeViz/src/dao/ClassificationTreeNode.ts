import Helpers from '@/visualizations/util/Helpers';

export default class ClassificationTreeNode {
    public idString: string;

    constructor(public fullKey: string[],
                public fullKeyAsString: string, // not in use yet, TODO this should replace idString...
                public key: string,
                public classifierId: number,
                public objects: number,
                public bytes: number,
                public children: ClassificationTreeNode[] | null) {
        this.idString = Helpers.createIdString(fullKey);
    }

    public clone(): ClassificationTreeNode {
        return Helpers.convertNodeRecursively(this);
    }

}
