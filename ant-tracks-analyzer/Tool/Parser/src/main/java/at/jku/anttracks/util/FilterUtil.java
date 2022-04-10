package at.jku.anttracks.util;

import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.FilterFactory;
import at.jku.anttracks.compiler.OnTheFlyCompiler;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.symbols.Symbols;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Supplier;

public class FilterUtil {

    public static final String CUSTOM_FILTER_FILE_EXTENSION = ".filter";

    public static FilterFactory loadFilter(File file, Supplier<IndexBasedHeap> fastHeapSupplier, Supplier<Symbols> symbolsSupplier)
            throws IOException, InstantiationException, IllegalAccessException {
        if (file != null) {
            try (BufferedReader in = new BufferedReader(new FileReader(file))) {
                String myName = in.readLine();
                String myDescription = in.readLine();
                String myCollectionType = in.readLine();
                String myDefinition = "";
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    myDefinition += line + "\n"; // i know i'm lazy here ...
                }
                return compile(myDefinition, myName, myDescription, myCollectionType, fastHeapSupplier, symbolsSupplier);
            }
        }

        return null;
    }

    public static FilterFactory compile(String myDefinition,
                                        String myName,
                                        String myDescription,
                                        String myCollectionType,
                                        Supplier<IndexBasedHeap> fastHeapSupplier,
                                        Supplier<Symbols> symbolsSupplier) throws IllegalAccessException, InstantiationException {
        FilterFactory newFilter = null;

        String sourceCode = getFilterStub().replace("###METHOD###", myDefinition)
                                           .replace("###NAME###", myName)
                                           .replace("###DESC###", myDescription)
                                           .replace("###COLLECTIONTYPE###", myCollectionType.toUpperCase());
        @SuppressWarnings("unchecked")
        Class<Filter> clazz = (Class<Filter>) new OnTheFlyCompiler().compile(sourceCode);
        Filter filter = clazz.newInstance();
        filter.setIsCustom(true);
        filter.setSourceCode(myDefinition);

        newFilter = new FilterFactory(symbolsSupplier, fastHeapSupplier, filter);
        return newFilter;
    }

    private static String getFilterStub() {
        InputStream in = FilterUtil.class.getResourceAsStream("/template/UserFilterStub.template");
        if (in == null) {
            try {
                in = new FileInputStream("./resources/template/UserFilterStub.template");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return "An error occurred during loading the template";
            }
        }

        return IOUtil.readInputStreamIntoString(in);
    }

    public static String getFilterTemplate() {
        InputStream in = FilterUtil.class.getResourceAsStream("/template/UserFilterMethodStub.template");
        if (in == null) {
            try {
                in = new FileInputStream("./resources/template/UserFilterMethodStub.template");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return "An error occurred during loading the template";
            }
        }
        return IOUtil.readInputStreamIntoString(in);
    }

    public static void storeFilterFile(String myDefinition,
                                       String myName,
                                       String myDescription,
                                       String myCollectionType,
                                       String filtersDirectory,
                                       String originalFilterName)
            throws IOException {
        new File(filtersDirectory).mkdirs();
        try (BufferedWriter out = new BufferedWriter(new FileWriter(new File(filtersDirectory + File.separator + myName + CUSTOM_FILTER_FILE_EXTENSION)))) {
            out.write(myName);
            out.write("\n");
            out.write(myDescription);
            out.write("\n");
            out.write(myCollectionType);
            out.write("\n");
            out.write(myDefinition);
        }

        // delete old classifier file if renamed
        if (originalFilterName != null && !myName.equals(originalFilterName)) {
            deleteFilterFile(originalFilterName, filtersDirectory);
        }
    }

    public static void deleteFilterFile(String filterName, String filtersDirectory) throws IOException {
        Files.deleteIfExists(Paths.get(filtersDirectory + File.separator + filterName + CUSTOM_FILTER_FILE_EXTENSION));
    }

}
