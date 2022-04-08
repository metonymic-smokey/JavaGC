package at.jku.anttracks.classification.enumerations;

public enum ClassifierSourceCollection {
    DETAILEDHEAP("DetailedHeap"),
    FASTHEAP("FastHeap"),
    ALL("All");

    private String text;

    ClassifierSourceCollection(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
