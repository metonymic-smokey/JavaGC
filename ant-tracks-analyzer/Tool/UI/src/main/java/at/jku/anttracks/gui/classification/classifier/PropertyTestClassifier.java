
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.symbols.Symbols;

import java.util.function.Supplier;

@C(name = "Property Test Classifier",
        desc = "",
        example = "Test",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.ALL)
public class PropertyTestClassifier extends Classifier<String> {

    @ClassifierProperty(overviewLevel = 0)
    private ClassifierChain classifierChain = new ClassifierChain(new TypeClassifier());

    public ClassifierChain getClassifierChain() {
        return classifierChain;
    }

    public void setClassifierChain(ClassifierChain classifierChain) {
        this.classifierChain = classifierChain;
    }

    @ClassifierProperty(overviewLevel = 0)
    private byte byteType = 1;

    public byte getByteType() {
        return byteType;
    }

    public void setByteType(byte byteType) {
        this.byteType = byteType;
    }

    @ClassifierProperty(overviewLevel = 0)
    private Byte byteClass = 1;

    public Byte getByteClass() {
        return byteClass;
    }

    public void setByteClass(Byte byteClass) {
        this.byteClass = byteClass;
    }

    @ClassifierProperty(overviewLevel = 0)
    private short shortType = 2;

    public short getShortType() {
        return shortType;
    }

    public void setShortType(short shortType) {
        this.shortType = shortType;
    }

    @ClassifierProperty(overviewLevel = 0)
    private Short shortClass = 2;

    public Short getShortClass() {
        return shortClass;
    }

    public void setShortClass(Short shortClass) {
        this.shortClass = shortClass;
    }

    @ClassifierProperty(overviewLevel = 0)
    private int integerType = 3;

    public int getIntegerType() {
        return integerType;
    }

    public void setIntegerType(int integerType) {
        this.integerType = integerType;
    }

    @ClassifierProperty(overviewLevel = 0)
    private Integer integerClass = 3;

    public Integer getIntegerClass() {
        return integerClass;
    }

    public void setIntegerClass(Integer integerClass) {
        this.integerClass = integerClass;
    }

    @ClassifierProperty(overviewLevel = 0)
    private long longType = 4l;

    public long getLongType() {
        return longType;
    }

    public void setLongType(long longType) {
        this.longType = longType;
    }

    @ClassifierProperty(overviewLevel = 0)
    private Long longClass = 4l;

    public Long getLongClass() {
        return longClass;
    }

    public void setLongClass(Long longClass) {
        this.longClass = longClass;
    }

    @ClassifierProperty(overviewLevel = 0)
    private float floatType = 5.0f;

    public float getFloatType() {
        return floatType;
    }

    public void setFloatType(float floatType) {
        this.floatType = floatType;
    }

    @ClassifierProperty(overviewLevel = 0)
    private Float floatClass = 5.0f;

    public Float getFloatClass() {
        return floatClass;
    }

    public void setFloatClass(Float floatClass) {
        this.floatClass = floatClass;
    }

    @ClassifierProperty(overviewLevel = 0)
    private double doubleType = 6.0;

    public double getDoubleType() {
        return doubleType;
    }

    public void setDoubleType(double doubleType) {
        this.doubleType = doubleType;
    }

    @ClassifierProperty(overviewLevel = 0)
    private Double doubleClass = 6.0;

    public Double getDoubleClass() {
        return doubleClass;
    }

    public void setDoubleClass(Double doubleClass) {
        this.doubleClass = doubleClass;
    }

    @ClassifierProperty(overviewLevel = 0)
    private boolean booleanType = true;

    public boolean getBooleanType() {
        return booleanType;
    }

    public void setBooleanType(boolean booleanType) {
        this.booleanType = booleanType;
    }

    @ClassifierProperty(overviewLevel = 0)
    private Boolean booleanClass = true;

    public Boolean getBooleanClass() {
        return booleanClass;
    }

    public void setBooleanClass(Boolean booleanClass) {
        this.booleanClass = booleanClass;
    }

    @ClassifierProperty(overviewLevel = 0)
    private char charType = 'A';

    public char getCharType() {
        return charType;
    }

    public void setCharType(char charType) {
        this.charType = charType;
    }

    @ClassifierProperty(overviewLevel = 0)
    private Character charClass = 'A';

    public Character getCharClass() {
        return charClass;
    }

    public void setCharClass(Character charClass) {
        this.charClass = charClass;
    }

    /*
     * @ClassifierProperty(overviewLevel = 0) private boolean missingSetter;
     *
     * public boolean getMissingSetter() { return missingSetter; }
     *
     * @ClassifierProperty(overviewLevel = 0) private boolean missingGetter;
     *
     * public void setMissingGetter(boolean missingGetter) { this.missingGetter
     * = missingGetter; }
     *
     * @ClassifierProperty(overviewLevel = 0) private boolean
     * missingGetterAndSetter;
     */

    @SuppressWarnings("unchecked")
    @Override
    public void setup(Supplier<Symbols> symbols, Supplier<IndexBasedHeap> heap) {
        super.setup(symbols, heap);
        classifierChain.forEach(aaoc -> aaoc.setup(symbols, heap));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setup(Supplier<Symbols> symbols) {
        super.setup(symbols);
        classifierChain.forEach(aaoc -> aaoc.setup(symbols));
    }

    @Override
    public String classify() {
        return "Test";
    }
}
