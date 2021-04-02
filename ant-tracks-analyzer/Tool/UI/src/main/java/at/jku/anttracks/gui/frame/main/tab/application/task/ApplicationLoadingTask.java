
package at.jku.anttracks.gui.frame.main.tab.application.task;

import at.jku.anttracks.gui.frame.main.tab.application.ApplicationTab;
import at.jku.anttracks.gui.frame.main.tab.application.controller.ApplicationController;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.AntTask;
import at.jku.anttracks.gui.utils.Consts;
import at.jku.anttracks.gui.utils.MetaParserTask;
import at.jku.anttracks.gui.utils.WindowUtil;
import at.jku.anttracks.heap.GarbageCollectionType;
import at.jku.anttracks.heap.StatisticGCInfo;
import at.jku.anttracks.heap.statistics.Statistics;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.heap.symbols.SymbolsFileVersionNotMatchingException;
import at.jku.anttracks.parser.TraceFile;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.parser.symbols.SymbolsFile;
import at.jku.anttracks.parser.symbols.SymbolsParser;
import at.jku.anttracks.util.FileUtil;
import at.jku.anttracks.util.ThreadUtil;
import at.jku.anttracks.util.ZipFileUtil;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;

public class ApplicationLoadingTask extends AntTask<List<Statistics>> {
    private final ButtonType noKeepExistingFiles = new ButtonType("No, keep existing data", ButtonData.NO);
    private final ButtonType yesRebuild = new ButtonType("Yes, rebuild", ButtonData.YES);
    private final AppInfo appInfo;
    private final ApplicationTab tab;

    private String metaHeaderFilePath;
    private String metaFeatureFilePath;

    public ApplicationLoadingTask(ApplicationTab tab, AppInfo appInfo) {
        this.tab = tab;
        this.appInfo = appInfo;
    }

    private void parseSymbols(File featureFile) throws Exception {
        updateMessage("Parsing symbols file");
        // Parse symbols
        SymbolsParser symbolsParser = new SymbolsParser(appInfo.getSymbolsFile(), appInfo.getClassDefinitionsFile(), featureFile, Symbols.CALLCONTEXT_NONE);
        try {
            appInfo.setSymbols(symbolsParser.parse());
        } catch (SymbolsFileVersionNotMatchingException e) {
            // Symbols file version not matching
            final FutureTask<Void> alertTask = new FutureTask<>(() -> {
                Alert alert = new Alert(AlertType.ERROR, e.getMessage());
                alert.setTitle("Error: Invalid symbols file version");
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                WindowUtil.INSTANCE.centerInMainFrame(alert);
                alert.showAndWait();
                return null;
            });
            Platform.runLater(alertTask);
            alertTask.get();
        } catch (Exception e) {
            // Other error during symbols parsing
            final FutureTask<Boolean> alertTask = new FutureTask<>(() -> {
                Alert alert = new Alert(AlertType.ERROR,
                                        "An internal error occured while parsing, do you want to retry?\n" + e.toString(),
                                        ButtonType.YES,
                                        ButtonType.NO);
                alert.setTitle("Error");
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                WindowUtil.INSTANCE.centerInMainFrame(alert);
                Optional<ButtonType> retryChoice = alert.showAndWait();
                return retryChoice.isPresent() && retryChoice.get() == ButtonType.YES;
            });
            Platform.runLater(alertTask);
            if (alertTask.get()) {
                parseSymbols(featureFile);
            } else {
                throw new Exception(e);
            }
        }
    }

    private boolean getUserReparseDecision() throws ExecutionException, InterruptedException {
        getLOGGER().log(Level.INFO, "feature information requires reparse");

        String alertText, alertTitle;
        if (BaseFile.exists(metaFeatureFilePath)) {
            // feature file changed (meta feature file does not match selected one)
            getLOGGER().log(Level.INFO, "found not inconsistent feature files -> ask user");
            alertText = "The feature mapping file does not match the last used feature file!\n" + "Would you like to reparse?\n" + "Select yes if you want to show " +
                    "features based" +
                    " on new feature file, no if you want to show features based on the " + "old feature file.";
            alertTitle = "Found inconsistent feature files";

        } else {
            // feature file added (meta data does not contain feature information)
            getLOGGER().log(Level.INFO, "user added feature files -> ask user");
            alertText = "Feature mapping has been added after parsing!\n" + "If you want to show feature information, the trace has to be reparsed. Otherwise, feature " +
                    "information cannot be " + "shown.\n" + "Would you like to reparse the trace?";
            alertTitle = "Feature mapping added";
        }

        final FutureTask<Boolean> alertTask = new FutureTask<>(() -> {
            Alert alert = new Alert(AlertType.INFORMATION, alertText, noKeepExistingFiles, yesRebuild);
            alert.setTitle(alertTitle);
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            ((Button) (alert.getDialogPane().lookupButton(noKeepExistingFiles))).setPrefWidth(250);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == yesRebuild;
        });

        Platform.runLater(alertTask);
        boolean reparse = alertTask.get();
        getLOGGER().log(Level.INFO, "user input: reparse = " + reparse);
        return reparse;
    }

    @Override
    protected List<Statistics> backgroundWork() throws Exception {
        updateTitle("Setup " + appInfo.getAppName() + " (File: " + appInfo.getSelectedTraceFile() + ")");

        // Set file paths
        appInfo.setMetaDataPath((BaseFile.isPlainFile(SymbolsFile.SYMBOL_FILE_ID, appInfo.getSymbolsFile()) ?
                                 appInfo.getSymbolsFile().getParent() :
                                 appInfo.getSymbolsFile()).toString() + File.separator + Consts.ANT_META_DIRECTORY);
        metaHeaderFilePath = appInfo.getMetaDataPath() + File.separator + Consts.HEADERS_META_FILE;
        metaFeatureFilePath = appInfo.getMetaDataPath() + File.separator + Consts.FEATURES_META_FILE;

        appInfo.setParsingCompleted(false);

        if (BaseFile.exists(appInfo.getMetaDataPath() + File.separator)) {
            // metadata is present
            if (TraceFile.hasValidMetaData(appInfo.getSelectedTraceFile(), metaHeaderFilePath)) {
                // metadata is valid for trace file
                if (hasValidFeatureMetaData()) {
                    // metadata is valid for trace and feature file
                    if (BaseFile.exists(metaFeatureFilePath)) {
                        // Meta file exists -> Copy to temp file since it could be stored inside zip file
                        File tempFeatureFile = File.createTempFile("AntTracks-temp-", ".feature");
                        tempFeatureFile.deleteOnExit();
                        // nio only handles zip targets correct. Use own copy implementation to copy from (potential) zips
                        //Files.copy(new File(metaFeatureFilePath).toPath(), tempFeatureFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        BaseFile.copy(metaFeatureFilePath, tempFeatureFile.getAbsolutePath());

                        appInfo.setSelectedFeaturesFile(tempFeatureFile);
                        parseSymbols(tempFeatureFile);
                    } else {
                        appInfo.setSelectedFeaturesFile(null);
                        parseSymbols(null);
                    }
                } else {
                    // metadata is not valid for feature file -> ask user whether he wants to reparse (just for features)
                    boolean reparse = getUserReparseDecision();
                    if (reparse) {
                        // user wants to reparse -> use selected features file
                        parseSymbols(appInfo.getSelectedFeaturesFile());
                        parseTrace();

                        // WOW, nio can handle zip files!
                        Files.copy(appInfo.getSelectedFeaturesFile().toPath(), new File(metaFeatureFilePath).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        // user does not want to reparse -> abort and leave everything as it is
                        if (BaseFile.exists(metaFeatureFilePath)) {
                            // Meta file exists -> Copy to temp file since it could be stored inside zip file
                            File tempFeatureFile = File.createTempFile("AntTracks-temp-", ".feature");
                            tempFeatureFile.deleteOnExit();
                            BaseFile.copy(metaFeatureFilePath, tempFeatureFile.getAbsolutePath());

                            appInfo.setSelectedFeaturesFile(tempFeatureFile);
                            parseSymbols(tempFeatureFile);
                        } else {
                            appInfo.setSelectedFeaturesFile(null);
                            parseSymbols(null);
                        }
                    }
                }
            } else {
                // metadata is not valid for trace file -> reparse
                parseSymbols(appInfo.getSelectedFeaturesFile());
                parseTrace();
            }

        } else {
            // no metadata present -> use appinfo featurefile
            parseSymbols(appInfo.getSelectedFeaturesFile());
            parseTrace();
        }

        appInfo.setParsingCompleted(true);
        Platform.runLater(() -> appInfo.setShowFeatures(appInfo.getSymbols().features != null));

        return Statistics.Companion.readStatisticsFromMetadata(appInfo.getMetaDataPath(), appInfo.getSymbols());
    }

    private boolean hasValidFeatureMetaData() throws IOException {
        updateMessage("Checking feature meta data");
        File features = appInfo.getSelectedFeaturesFile();
        File metaFeaturesCopy = new File(metaFeatureFilePath);

        // feature meta data is valid if ...
        if (features == null) {
            // ...no feature file was set by the user -> meta feature file is taken
            return true;

        } else if (metaFeaturesCopy.exists() || ZipFileUtil.exists(metaFeaturesCopy)) {
            getLOGGER().log(Level.INFO, "found feature files. checking them.");

            byte[] b_f = FileUtil.readAllBytes(ZipFileUtil.isZipFilePath(features.getAbsolutePath()) ? ZipFileUtil.openR(features) : FileUtil.openR(features));
            byte[] b_f_copy = FileUtil.readAllBytes(ZipFileUtil.isZipFilePath(metaFeaturesCopy.getAbsolutePath()) ?
                                                    ZipFileUtil.openR(metaFeaturesCopy) :
                                                    FileUtil.openR(metaFeaturesCopy));

            // ...or the selected and meta feature file match
            return Arrays.equals(b_f, b_f_copy);

        }

        return false;
    }

    private void parseTrace() throws Exception {
        updateMessage("Parse trace file");
        MetaParserTask heapParsingTask = new MetaParserTask(appInfo, tab, metaHeaderFilePath);
        ThreadUtil.startTask(heapParsingTask).get();
    }

    @Override
    protected void finished() {
        try {
            appInfo.getStatistics().clear();
            appInfo.getStatistics().addAll(get());
            if (appInfo.getStatistics() != null) {
                getLOGGER().log(Level.INFO, "plotting overview");
                ApplicationTabPlottingTask currentChartUpdatingTask = new ApplicationTabPlottingTask(appInfo, tab);
                ThreadUtil.startTask(currentChartUpdatingTask);
                setMetricText();
                ApplicationController.INSTANCE.startSuitableTimeWindowsDetectionTask(tab.getOverviewTab(),
                                                                                     tab.getDetailTab(),
                                                                                     tab,
                                                                                     appInfo);
            } else {
                getLOGGER().log(Level.WARNING, "aborting plotting overview (due to previous error)");
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    private void setMetricText() {
        if (appInfo.getStatistics().isEmpty()) {
            tab.setMetricText("No GC happened!");
            return;
        }

        long runTime = appInfo.getStatistics().get(appInfo.getStatistics().size() - 1).getInfo().getTime();
        int minorCount = 0;
        long minorTime = 0;
        int majorCount = 0;
        long majorTime = 0;
        int otherCount = 0;
        long otherTime = 0;

        for (int i = 0; i + 1 < appInfo.getStatistics().size(); i += 2) {
            StatisticGCInfo startInfo = appInfo.getStatistics().get(i).getInfo();
            StatisticGCInfo endInfo = appInfo.getStatistics().get(i + 1).getInfo();
            long start = startInfo.getTime();
            long end = endInfo.getTime();
            long duration = end - start;

            if (startInfo.getType() == GarbageCollectionType.MINOR) {
                minorCount++;
                minorTime += duration;
            } else if (startInfo.getType() == GarbageCollectionType.MAJOR) {
                majorCount++;
                majorTime += duration;
            } else {
                otherCount++;
                otherTime += duration;
            }
        }

        int overallCount = minorCount + majorCount + otherCount;
        long overallTime = minorTime + majorTime + otherTime;

        tab.setMetricText(String.format("Run time:\t\t\t%,d ms\n" + "Overall GC time:\t%,d ms\t(%.2f%%)\t- %d GCs (%,.2f ms avg.)\n" + "Minor GC time:\t\t%,d ms\t" +
                                                "(%.2f%%)\t- %d " +
                                                "GCs (%,.2f ms avg.)\n" + "Major GC time:\t\t%,d ms\t(%.2f%%)\t- %d GCs (%,.2f ms avg.)\n" + "Other GC time:\t\t%,d " +
                                                "ms\t(%.2f%%)\t- " +
                                                "%d GCs (%,.2f ms avg.)\n",
                                        runTime,
                                        overallCount,
                                        (100.0 * overallTime) / (1.0 * runTime),
                                        overallCount,
                                        overallCount > 0 ? (1.0 * overallTime) / (1.0 * overallCount) : 0,
                                        minorTime,
                                        (100.0 * minorTime) / (1.0 * runTime),
                                        minorCount,
                                        minorCount > 0 ? (1.0 * minorTime) / (1.0 * minorCount) : 0,
                                        majorTime,
                                        (100.0 * majorTime) / (1.0 * runTime),
                                        majorCount,
                                        majorCount > 0 ? (1.0 * majorTime) / (1.0 * majorCount) : 0,
                                        otherTime,
                                        (100.0 * otherTime) / (1.0 * runTime),
                                        otherCount,
                                        otherCount > 0 ? (1.0 * otherTime) / (1.0 * otherCount) : 0));
    }
}