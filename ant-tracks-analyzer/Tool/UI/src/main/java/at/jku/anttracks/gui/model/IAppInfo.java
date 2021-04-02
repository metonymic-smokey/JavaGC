
package at.jku.anttracks.gui.model;

import at.jku.anttracks.heap.Tag;
import at.jku.anttracks.heap.statistics.Statistics;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.ParsingInfo;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

public interface IAppInfo {
    public interface AppInfoListener {
        void appInfoChanged(ChangeType type);
    }

    public enum AliveDeadPanelType {
        N_OBJECTS,
        BYTE,
        RELATIVE
    }

    public enum ChangeType {
        NAME,
        FILE,
        TRACE,
        SYMBOLS,
        FEATURE,
        SHOW_FEATURES,
        DATA_STRUCTURE,
        ALIVE_DEAD_PANEL_TYPE
    }

    public ParsingInfo getParsingInfo();

    public void setParsingInfo(ParsingInfo parsingInfo);

    public File getSelectedTraceFile();

    public File getSymbolsFile() throws IOException;

    public File getClassDefinitionsFile() throws IOException;

    public Symbols getSymbols();

    public List<Statistics> getStatistics();

    public List<Statistics> getStatistics(long time);

    public long getTraceStart();

    public long getTraceEnd();

    public long getTraceLengthInMilliseconds();

    public int getMaxOfEdenObjectCount(long fromTime, long toTime);

    public double getGCOverhead();

    public double getGCFrequency();

    public String getAppName();

    public File getSelectedFeaturesFile();

    public void setShowFeatures(boolean b);

    public boolean isShowFeatures();

    public void setSymbols(Symbols symbols);

    public void setAppName(String appName);

    public void setSelectedFeaturesFile(File selectedFeaturesFile);

    public void setSelectedTraceFile(File selectedTraceFile);

    public void selectTraceFile(File traceFile);

    public void selectFeaturesFile(File featuresFile);

    public void selectFeaturesAndTraceFile(File traceFile, File featuresFile);

    public void selectDataStructureDefinitionFile(URI selectedDataStructureDefinitionFile);

    public void selectDataStructureDefinitionFiles(List<URI> selectedDataStructureDefinitionFiles);

    public void addListener(AppInfoListener l);

    public void removeListener(AppInfoListener l);

    public void setAliveDeadPanelType(AliveDeadPanelType type);

    public AliveDeadPanelType getAliveDeadPanelType();

    public void setParsingCompleted(boolean b);

    public boolean isParsingCompleted();

    public void setMetaDataPath(String metaDir);

    public String getMetaDataPath();

    public List<Tag> getTags();

    public void setTags(List<Tag> tags);
}
