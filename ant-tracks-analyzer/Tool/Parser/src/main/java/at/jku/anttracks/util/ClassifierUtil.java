package at.jku.anttracks.util;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierFactory;
import at.jku.anttracks.compiler.OnTheFlyCompiler;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.symbols.Symbols;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Supplier;

public class ClassifierUtil {

    public static final String CUSTOM_CLASSIFIER_FILE_EXTENSION = ".classifier";

    public static ClassifierFactory loadClassifier(File file, Supplier<IndexBasedHeap> fastHeapSupplier, Supplier<Symbols> symbolsSupplier)
            throws InstantiationException, IllegalAccessException, IOException {
        if (file != null) {
            try (BufferedReader in = new BufferedReader(new FileReader(file))) {
                String myName = in.readLine();
                String myDescription = in.readLine();
                String myExample = in.readLine();
                String myCardinality = in.readLine();
                String myCollectionType = in.readLine();
                String myDefinition = "";
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    myDefinition += line + "\n"; // i know i'm lazy here ...
                }
                return compile(myDefinition, myName, myDescription, myExample, myCardinality, myCollectionType, fastHeapSupplier, symbolsSupplier);
            }
        }

        return null;
    }

    public static ClassifierFactory compile(String myDefinition,
                                            String myName,
                                            String myDescription,
                                            String myExample,
                                            String myCardinality,
                                            String myCollectionType,
                                            Supplier<IndexBasedHeap> fastHeapSupplier,
                                            Supplier<Symbols> symbolsSupplier) throws IllegalAccessException, InstantiationException {
        ClassifierFactory newClassifier = null;

        String sourceCode = getClassifierStub().replace("###METHOD###", myDefinition)
                                               .replace("###NAME###", myName)
                                               .replace("###DESC###", myDescription)
                                               .replace("###EXAMPLE###", myExample)
                                               .replace("###CARDINALITY###", myCardinality.toUpperCase().replace(" ", "_"))
                                               .replace("###COLLECTIONTYPE###", myCollectionType.toUpperCase());
        @SuppressWarnings("unchecked")
        Class<Classifier<?>> clazz = (Class<Classifier<?>>) new OnTheFlyCompiler().compile(sourceCode);
        Classifier<?> classifier = clazz.newInstance();
        classifier.setIsCustom(true);
        classifier.setSourceCode(myDefinition);

        newClassifier = new ClassifierFactory(symbolsSupplier, fastHeapSupplier, classifier);

        return newClassifier;
    }

    private static String getClassifierStub() {
        InputStream in = ClassifierUtil.class.getResourceAsStream("/template/UserClassifierStub.template");
        if (in == null) {
            try {
                in = new FileInputStream("./resources/template/UserClassifierStub.template");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return "An error occurred during loading the template";
            }
        }

        return IOUtil.readInputStreamIntoString(in);
    }

    public static String getClassifierTemplate() {
        InputStream in = ClassifierUtil.class.getResourceAsStream("/template/UserClassifierMethodStub.template");
        if (in == null) {
            try {
                in = new FileInputStream("./resources/template/UserClassifierMethodStub.template");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return "An error occurred during loading the template";
            }
        }
        return IOUtil.readInputStreamIntoString(in);
    }

    public static void storeClassifierFile(String myDefinition,
                                           String myName,
                                           String myDescription,
                                           String myExample,
                                           String myCardinality,
                                           String myCollectionType,
                                           String classifiersDirectory,
                                           String originalClassifierName) throws IOException {
        new File(classifiersDirectory).mkdirs();

        try (BufferedWriter out = new BufferedWriter(new FileWriter(new File(classifiersDirectory + File.separator + myName + CUSTOM_CLASSIFIER_FILE_EXTENSION)))) {
            out.write(myName);
            out.write("\n");
            out.write(myDescription);
            out.write("\n");
            out.write(myExample);
            out.write("\n");
            out.write(myCardinality);
            out.write("\n");
            out.write(myCollectionType);
            out.write("\n");
            out.write(myDefinition);
        }

        // delete old classifier file if renamed
        if (originalClassifierName != null && !myName.equals(originalClassifierName)) {
            deleteClassifierFile(originalClassifierName, classifiersDirectory);
        }
    }

    public static void deleteClassifierFile(String name, String classifiersDirectory) {
        try {
            Files.deleteIfExists(Paths.get(classifiersDirectory + File.separator + name + CUSTOM_CLASSIFIER_FILE_EXTENSION));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
