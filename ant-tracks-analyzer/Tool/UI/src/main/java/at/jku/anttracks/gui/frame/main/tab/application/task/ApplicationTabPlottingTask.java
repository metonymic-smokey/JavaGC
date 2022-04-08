package at.jku.anttracks.gui.frame.main.tab.application.task;

import at.jku.anttracks.gui.frame.main.tab.application.ApplicationTab;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.AntTask;

public class ApplicationTabPlottingTask extends AntTask<Boolean> {

    private final ApplicationTab tab;
    private final AppInfo appInfo;

    public ApplicationTabPlottingTask(AppInfo appInfo, ApplicationTab tab) {
        this.appInfo = appInfo;
        this.tab = tab;
        updateTitle("Plot overview charts");
    }

    @Override
    protected Boolean backgroundWork() throws Exception {
        updateMessage(String.format("%,d GC points", appInfo.getStatistics().size()));
        tab.plot();
        return null;
    }

    @Override
    public boolean showOnUI() {
        return false;
    }

    @Override
    protected void finished() {
    }
}