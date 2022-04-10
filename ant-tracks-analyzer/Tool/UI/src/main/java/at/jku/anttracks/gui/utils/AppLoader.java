
package at.jku.anttracks.gui.utils;

import at.jku.anttracks.gui.dialog.openfile.OpenHPROFFileDialog;
import at.jku.anttracks.gui.dialog.openfile.OpenTraceFileDialog;
import at.jku.anttracks.gui.frame.main.tab.application.ApplicationTab;
import at.jku.anttracks.gui.frame.main.tab.application.task.ApplicationLoadingTask;
import at.jku.anttracks.gui.frame.main.tab.heapstate.task.HprofParserTask;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.ClientInfo;
import at.jku.anttracks.util.ThreadUtil;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AppLoader {

    private final static Logger LOGGER = Logger.getLogger(AppLoader.class.getSimpleName());

    public static void preprocessDirectory() {
        // TODO TraceFileFilter filter = new TraceFileFilter();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(ClientInfo.traceDirectory));
        File traceFile = fileChooser.showOpenDialog(ClientInfo.stage);
        if (traceFile != null) {
            Alert terminateAntTracksAlert = new Alert(AlertType.CONFIRMATION,
                                                      "You are about to preprocess '" + traceFile.getAbsolutePath() + "' in the " + "background." + "\n" + "It is " +
                                                              "recommended " +
                                                              "that you terminate the application to increase the " + "available heap size." + "\n" + "Do you want to " +
                                                              "terminate now?",
                                                      ButtonType.YES,
                                                      ButtonType.NO,
                                                      ButtonType.CANCEL);
            terminateAntTracksAlert.setTitle("Preprocessing");
            terminateAntTracksAlert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(terminateAntTracksAlert);
            terminateAntTracksAlert.showAndWait().ifPresent(result -> {
                if (result != ButtonType.CANCEL) {
                    try {
                        Runtime.getRuntime()
                               .exec(new String[]{OsScript.getScriptExecutable(),
                                                  "runAntTracksPreprocess." + OsScript.getScriptExtension(),
                                                  traceFile.getAbsolutePath()});
                    } catch (IOException e) {
                        LOGGER.log(Level.SEVERE, "error occured", e);
                        Errors.display(e);
                    }
                    Alert preprocessStartedAlert = new Alert(AlertType.INFORMATION, "Preprocessing started in an external application.");
                    preprocessStartedAlert.setTitle("Preprocessing");
                    preprocessStartedAlert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                    WindowUtil.INSTANCE.centerInMainFrame(preprocessStartedAlert);
                    preprocessStartedAlert.showAndWait();
                }

                if (result == ButtonType.YES) {
                    System.exit(0);
                }
            });
        }
    }

    public static void loadAntTracksTrace() {
        OpenTraceFileDialog ofd = new OpenTraceFileDialog();
        ofd.init(null);
        WindowUtil.INSTANCE.centerInMainFrame(ofd);
        ofd.showAndWait().ifPresent(result -> {
            if (result) {
                AppInfo appInfo = new AppInfo(ofd.getApplicationName(), ofd.getTraceFile(), ofd.getFeaturesFile());
                parse(appInfo);
            }
        });
    }

    public static void loadHprof() {
        OpenHPROFFileDialog ofd = new OpenHPROFFileDialog();
        ofd.init(null);
        WindowUtil.INSTANCE.centerInMainFrame(ofd);
        ofd.showAndWait().ifPresent(result -> {
            if (result) {
                String path = Objects.requireNonNull(ofd.getHprofFile()).getAbsolutePath();
                loadHprof(path);
            }
        });
    }

    private static void loadHprof(String path) {
        HprofParserTask loadHprofTask = new HprofParserTask(path);
        ThreadUtil.startTask(loadHprofTask);
    }

    // TODO: This does not work if executed with a HPROF file loaded instead of a trace file (because OpenTraceFileDialog is used instead of OpenHRPOFFileDialog)
    public static void reload() {
        // Get current app info
        AppInfo appInfo = ClientInfo.getCurrentAppInfo();
        // User enters new definition
        OpenTraceFileDialog ofd = new OpenTraceFileDialog();
        ofd.init(appInfo);
        WindowUtil.INSTANCE.centerInMainFrame(ofd);
        ofd.showAndWait().ifPresent(result -> {
            if (result) {
                // Update app info with newly entered values
                Objects.requireNonNull(appInfo).setAppName(ofd.getApplicationName());
                appInfo.selectFeaturesAndTraceFile(ofd.getTraceFile(), ofd.getFeaturesFile());
            }
        });
    }

    public static void parse(AppInfo appInfo) {
        parse(appInfo, null);
    }

    public static void parse(AppInfo appInfo, ApplicationTab tab) {
        try {
            /*
             * SymbolsParser symbolsParser = new SymbolsParser(appInfo.getSymbolsFile(), appInfo.getClassDefinitionsFile(),
             * appInfo.getSelectedFeaturesFile(), Symbols.CALLCONTEXT_STATIC); appInfo.setSymbols(symbolsParser.parse());
             */

            // If view is not set, create new one
            if (tab == null) {
                tab = new ApplicationTab();
                tab.init(appInfo);
                ClientInfo.mainFrame.addAndSelectTab(tab);
            }

            ApplicationLoadingTask appLoadingTask = new ApplicationLoadingTask(tab, appInfo);
            tab.getTasks().add(appLoadingTask);
            ThreadUtil.startTask(appLoadingTask);
        } catch (Exception e) {
            Logger.getLogger(AppLoader.class.getSimpleName()).log(Level.SEVERE, "error occured", e);
            Errors.display(e);
        }
    }
}
