
package at.jku.anttracks.gui.model;

import at.jku.anttracks.heap.StatisticGCInfo;
import at.jku.anttracks.heap.Tag;
import at.jku.anttracks.heap.statistics.Statistics;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.ParsingInfo;
import at.jku.anttracks.parser.classdefinitions.ClassDefinitionsFile;
import at.jku.anttracks.parser.symbols.SymbolsFile;
import com.google.common.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class AppInfo implements IAppInfo {
    private String appName;
    private Symbols symbols;
    private File selectedTraceFile;
    private File selectedFeaturesFile;
    private Set<URI> selectedDataStructureDefinitionFiles;
    private List<Statistics> statistics;
    private Map<Long, List<Statistics>> statisticsByTime;
    private long statisticsByTimeDeepSize = 0;
    private ParsingInfo parsingInfo;

    private List<Tag> tags;
    private String metaDir;
    // By default feature charts are shown (but only if feature file is loaded, see isShowFeatures())
    private boolean isShowFeatures = true;

    private final CopyOnWriteArrayList<AppInfoListener> listener = new CopyOnWriteArrayList<>();
    private AliveDeadPanelType aliveDeadType;
    private boolean parsingCompleted;

    public AppInfo(String appName, File selectedTraceFile, File selectedFeaturesFile) {
        this(appName, null, selectedTraceFile, selectedFeaturesFile);
    }

    public AppInfo(String appName, Symbols symbols, File selectedTraceFile, File selectedFeaturesFile) {
        this.appName = appName;
        this.symbols = symbols;

        this.selectedTraceFile = selectedTraceFile;
        this.selectedFeaturesFile = selectedFeaturesFile;
        this.selectedDataStructureDefinitionFiles = new HashSet<>();
        addListener(changeType -> {
            if ((changeType == ChangeType.SYMBOLS || changeType == ChangeType.DATA_STRUCTURE) && this.symbols != null) {
                // push all selected data structure definition files to symbols
                this.symbols.addDataStructureDefinitionFiles(selectedDataStructureDefinitionFiles);
            }
        });

        statistics = new ArrayList<>();
        statisticsByTime = new HashMap<>();
        isShowFeatures = selectedFeaturesFile != null;
        aliveDeadType = AliveDeadPanelType.RELATIVE;
        parsingCompleted = false;
        tags = new ArrayList<>();
    }

    @Override
    public ParsingInfo getParsingInfo() {
        return parsingInfo;
    }

    @Override
    public void setParsingInfo(ParsingInfo parsingInfo) {
        this.parsingInfo = parsingInfo;
    }

    @Override
    public File getSelectedTraceFile() {
        return selectedTraceFile;
    }

    @Override
    public File getSymbolsFile() throws IOException {
        if (selectedTraceFile == null) {
            return null;
        }
        return SymbolsFile.findSymbolsFileToTrace(selectedTraceFile);
    }

    @Override
    public File getClassDefinitionsFile() throws IOException {
        if (selectedTraceFile == null) {
            return null;
        }
        return ClassDefinitionsFile.findClassDefinitionsFileToTrace(selectedTraceFile);
    }

    @Override
    public Symbols getSymbols() {
        return symbols;
    }

    @Override
    public List<Statistics> getStatistics() {
        return statistics;
    }

    @Override
    public List<Statistics> getStatistics(long time) {
        if (statistics.size() > statisticsByTimeDeepSize) {
            statistics.stream().skip(statisticsByTimeDeepSize)
                      .forEach(stat -> {
                          statisticsByTime.merge(stat.getInfo().getTime(),
                                                 Lists.newArrayList(stat),
                                                 (list1, list2) -> {
                                                     list1.addAll(list2);
                                                     return list1;
                                                 });
                          statisticsByTimeDeepSize++;
                      });
        }

        return statisticsByTime.get(time);
    }

    @Override
    public long getTraceStart() {
        return !statistics.isEmpty() ? statistics.get(0).getInfo().getTime() : 0;
    }

    @Override
    public long getTraceEnd() {
        return !statistics.isEmpty() ? statistics.get(statistics.size() - 1).getInfo().getTime() : 0;
    }

    @Override
    public long getTraceLengthInMilliseconds() {
        return !statistics.isEmpty() ? statistics.get(statistics.size() - 1).getInfo().getTime() - statistics.get(0).getInfo().getTime() : 0;
    }

    @Override
    public int getMaxOfEdenObjectCount(long fromTime, long toTime) {
        return (int) statistics.stream()
                               .filter(stat -> stat.getInfo().getTime() >= fromTime && stat.getInfo().getTime() <= toTime)
                               .mapToLong(stat -> stat.getEden().memoryConsumption.getObjects())
                               .max()
                               .orElse(0);
    }

    @Override
    public double getGCOverhead() {
        long lastGCStartTime = 0;
        long gcDuration = 0;

        for (int i = 0; i < statistics.size(); i++) {
            StatisticGCInfo gcInfo = statistics.get(i).getInfo();

            if (gcInfo.getMeta() == EventType.GC_START) {
                lastGCStartTime = gcInfo.getTime();

            } else if (gcInfo.getMeta() == EventType.GC_END && i != 0) {
                gcDuration += gcInfo.getTime() - lastGCStartTime;
            }
        }

        return gcDuration / (double) getTraceLengthInMilliseconds();
    }

    @Override
    public double getGCFrequency() {
        if (!statistics.isEmpty()) {
            int gcCount = statistics.size() / 2;
            // return no of gcs per second(!) of runtime
            return gcCount / (getTraceLengthInMilliseconds() / 1000.0);
        }

        return 0;
    }

    @Override
    public String getAppName() {
        return appName;
    }

    @Override
    public File getSelectedFeaturesFile() {
        return selectedFeaturesFile;
    }

    @Override
    public void setShowFeatures(boolean b) {
        if (isShowFeatures != b) {
            isShowFeatures = b;
            fireListener(ChangeType.SHOW_FEATURES);
        }
    }

    @Override
    public boolean isShowFeatures() {
        return isShowFeatures && selectedFeaturesFile != null;
    }

    @Override
    public void setSymbols(Symbols symbols) {
        if (this.symbols != symbols) {
            this.symbols = symbols;
            fireListener(ChangeType.SYMBOLS);
        }
    }

    @Override
    public void setAppName(String appName) {
        if (!this.appName.equals(appName)) {
            this.appName = appName;
            fireListener(ChangeType.NAME);
        }
    }

    @Override
    public void setSelectedFeaturesFile(File selectedFeaturesFile) {
        this.selectedFeaturesFile = selectedFeaturesFile;
    }

    @Override
    public void setSelectedTraceFile(File selectedTraceFile) { this.selectedTraceFile = selectedTraceFile; }

    @Override
    public void selectTraceFile(File traceFile) {
        // sets trace file and fires event on change
        if (fileChanged(traceFile, selectedTraceFile)) {
            setSelectedTraceFile(traceFile);
            fireListener(ChangeType.TRACE);
        }
    }

    @Override
    public void selectFeaturesFile(File featuresFile) {
        // sets feature file and fires event on change
        if (fileChanged(featuresFile, selectedFeaturesFile)) {
            setSelectedFeaturesFile(featuresFile);
            fireListener(ChangeType.FEATURE);
        }
    }

    @Override
    public void selectFeaturesAndTraceFile(File traceFile, File featuresFile) {
        // sets feature and trace file and fires SINGLE event on change (avoid multiple parser tasks on redefine)
        if (fileChanged(traceFile, selectedTraceFile) || fileChanged(featuresFile, selectedFeaturesFile)) {
            setSelectedTraceFile(traceFile);
            setSelectedFeaturesFile(featuresFile);
            fireListener(ChangeType.FILE);
        }
    }

    @Override
    public void selectDataStructureDefinitionFile(URI selectedDataStructureDefinitionFile) {
        if (selectedDataStructureDefinitionFiles.add(selectedDataStructureDefinitionFile)) {
            fireListener(ChangeType.DATA_STRUCTURE);
        }
    }

    @Override
    public void selectDataStructureDefinitionFiles(List<URI> selectedDataStructureDefinitionFiles) {
        if (this.selectedDataStructureDefinitionFiles.addAll(selectedDataStructureDefinitionFiles)) {
            fireListener(ChangeType.DATA_STRUCTURE);
        }
    }

    @Override
    public void addListener(AppInfoListener l) {
        listener.add(l);
    }

    @Override
    public void removeListener(AppInfoListener l) {
        listener.remove(l);
    }

    @Override
    public void setAliveDeadPanelType(AliveDeadPanelType type) {
        aliveDeadType = type;
        fireListener(ChangeType.ALIVE_DEAD_PANEL_TYPE);
    }

    @Override
    public AliveDeadPanelType getAliveDeadPanelType() {
        return aliveDeadType;
    }

    @Override
    public void setParsingCompleted(boolean b) {
        this.parsingCompleted = b;
    }

    @Override
    public boolean isParsingCompleted() {
        return parsingCompleted;
    }

    @Override
    public void setMetaDataPath(String metaDir) {
        this.metaDir = metaDir;
    }

    @Override
    public String getMetaDataPath() {
        return metaDir;
    }

    @Override
    public List<Tag> getTags() {
        return tags;
    }

    @Override
    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    private boolean fileChanged(File before, File after) {
        try {
            return before == null && after != null || before != null && after == null || !before.getCanonicalPath().equals(after.getCanonicalPath());
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        }
    }

    private void fireListener(ChangeType t) {
        listener.stream().forEach(x -> x.appInfoChanged(t));
    }
}
