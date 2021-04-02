
package at.jku.anttracks.gui.frame.main;

import at.jku.anttracks.gui.dialog.preference.Settings;
import at.jku.anttracks.gui.io.websocket.AntTracksWebSocketServer;
import at.jku.anttracks.gui.model.ClientInfo;
import at.jku.anttracks.gui.utils.Consts;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.gui.utils.OperationManager;
import at.jku.anttracks.heap.MemoryMappedFastHeap;
import at.jku.anttracks.parser.TraceParser;
import at.jku.anttracks.util.ApplicationStatistics;
import at.jku.anttracks.util.GCReporter;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import kotlin.Unit;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

import static at.jku.anttracks.gui.utils.ShutdownUtilKt.addShutdownHook;

public class GUI extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            try {
                Files.list(MemoryMappedFastHeap.getTempDir()).forEach(f -> {
                    try {
                        Files.delete(f);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (IOException e) {
                // Could not open directory, do nothing
            }

            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(GUI.class.getResource("MainFrame.fxml"));

            // Workaround because GTKLookAndFeel is not working in JavaFX
            // application (application hangs up on UIManager.setLookAndFeel())
            SwingUtilities.invokeAndWait(() -> {
                try {
                    for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                        if (info.getClassName().equals("javax.swing.plaf.nimbus.NimbusLookAndFeel")) {
                            UIManager.setLookAndFeel(info.getClassName());
                        }
                    }
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
                    e.printStackTrace();
                }
            });

            Pane root = loader.load();
            MainFrame mainFrame = loader.getController();
            ClientInfo.init(this, mainFrame, primaryStage, new OperationManager(mainFrame.getStatusPane().getChildren()));
            mainFrame.init();

            Settings.getInstance().setup(); // just make sure it is created ...

            try {
                AntTracksWebSocketServer webSocketServer = new AntTracksWebSocketServer(new InetSocketAddress(8887));
                webSocketServer.start();
            } catch (Exception ex) {
                System.err.println("Web Socket could not be started, most probably because port 8887 is already in use (is another instance of AntTracks already running?)!");
            }

            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            List<String> jvmArgs = runtimeMXBean.getInputArguments();
            System.out.println("JVM arguments:");
            for (String arg : jvmArgs) {
                System.out.println(arg);
            }

            // 1. Consistency check
            String consistencyCheckString = "at.jku.mevss.trace.consistencycheck";
            if (System.getProperty(consistencyCheckString) == null) {
                System.setProperty(consistencyCheckString, "false");
            }
            TraceParser.CONSISTENCY_CHECK = Boolean.parseBoolean(System.getProperty(consistencyCheckString));
            ClientInfo.consistencyCheck = TraceParser.CONSISTENCY_CHECK;
            System.out.println("Consistency check: " + System.getProperty(consistencyCheckString));

            // 2. Logging
            String loggingConfigFileString = "java.util.logging.config.file";
            if (System.getProperty(loggingConfigFileString) == null) {
                System.getProperties().setProperty(loggingConfigFileString, "log.config");
            }
            String logFile = System.getProperty(loggingConfigFileString);
            ClientInfo.logSettingsFile = logFile;
            System.out.println("Log config file: " + logFile);

            // 3. Statistics output path
            String statisticsPathString = "at.jku.anttracks.gui.printStatisticsPath";
            ClientInfo.printStatisticsPath = System.getProperty(statisticsPathString);
            System.out.println("Statistics result file: " + (ClientInfo.printStatisticsPath == null ? "null" : ClientInfo.printStatisticsPath));

            addShutdownHook(() -> {
                if (ClientInfo.printStatisticsPath != null) {
                    ApplicationStatistics.getInstance()
                                         .export(new File(ClientInfo.printStatisticsPath.replace("%d",
                                                                                                 new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date(System.currentTimeMillis())))
                                                                                        .replace(" ", "")));
                }
                ApplicationStatistics.getInstance().print();

                try {
                    Files.list(MemoryMappedFastHeap.getTempDir()).forEach(f -> {
                        try {
                            Files.delete(f);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                } catch (IOException e) {
                    // Could not open directory, do nothing
                }
                return Unit.INSTANCE;
            });

            // 4. Assertions
            boolean assertionsEnabled = false;
            assert assertionsEnabled = true;
            ClientInfo.assertionsEnabled = assertionsEnabled;
            System.out.println("Assertions enabled: " + assertionsEnabled);

            GCReporter.getInstance(); // just make sure it is running ...

            Scene scene = new Scene(root);
            scene.setOnKeyReleased(new EventHandler<KeyEvent>() {
                private Set<KeyCode> fKeys = new HashSet<>(Arrays.asList(KeyCode.F1,
                                                                         KeyCode.F2,
                                                                         KeyCode.F3,
                                                                         KeyCode.F4,
                                                                         KeyCode.F5,
                                                                         KeyCode.F6,
                                                                         KeyCode.F7,
                                                                         KeyCode.F8,
                                                                         KeyCode.F9,
                                                                         KeyCode.F10,
                                                                         KeyCode.F11,
                                                                         KeyCode.F12));

                private Set<KeyCode> f1to6Keys = new HashSet<>(Arrays.asList(KeyCode.F1,
                                                                             KeyCode.F2,
                                                                             KeyCode.F3,
                                                                             KeyCode.F4,
                                                                             KeyCode.F5,
                                                                             KeyCode.F6));

                @Override
                public void handle(KeyEvent event) {
                    if (event.isControlDown() && fKeys.contains(event.getCode())) {
                        WritableImage snapshot = scene.snapshot(null);
                        File saveFile;
                        if (f1to6Keys.contains(event.getCode())) {
                            String desktop = System.getProperty("user.home") + "/Desktop/";
                            String currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                            saveFile = new File(desktop + "AntTracksScreenshot_" + currentDateTime + ".png");
                        } else {
                            FileChooser fileChooser = new FileChooser();
                            saveFile = fileChooser.showSaveDialog(primaryStage);
                        }
                        if (saveFile != null) {
                            try {
                                //ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
                                try (FileOutputStream fileOutputStream = new FileOutputStream(saveFile)) {
                                    ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", fileOutputStream);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
            primaryStage.setTitle("AntTracks Analyzer");
            primaryStage.setScene(scene);
            primaryStage.getIcons().add(ImageUtil.getIconNode(Consts.ANT_ICON_IMAGE).getImage());
            primaryStage.setMaximized(true);
            // TODO is not necessary if all threads are closed properly
            primaryStage.setOnCloseRequest(e -> {
                Platform.exit();
                System.exit(0);
            });

            primaryStage.show();

            SplashScreen screen = SplashScreen.getSplashScreen();
            if (screen != null && screen.isVisible()) {
                screen.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
