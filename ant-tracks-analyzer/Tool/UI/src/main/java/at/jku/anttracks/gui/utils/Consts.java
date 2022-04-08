
package at.jku.anttracks.gui.utils;

import at.jku.anttracks.util.ImagePack;
import javafx.scene.image.ImageView;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.logging.Logger;

public class Consts {
    public static final String HEADERS_META_FILE = at.jku.anttracks.util.Consts.HEADERS_META_FILE;
    public static final String HEAP_INDEX_META_FILE = at.jku.anttracks.util.Consts.HEAP_INDEX_META_FILE;
    public static final String STATISTICS_META_FILE = at.jku.anttracks.util.Consts.STATISTICS_META_FILE;
    public static final String ANT_META_DIRECTORY = at.jku.anttracks.util.Consts.ANT_META_DIRECTORY;
    public static final String FEATURES_META_FILE = at.jku.anttracks.util.Consts.FEATURES_META_FILE;
    public static final String CONFIG_DIRECTORY = System.getProperty("user.home") + File.separator + ".ant_tracks" + File.separator;
    public static final String SETTINGS_FILE = CONFIG_DIRECTORY + "tool_settings";
    public static final String CLASSIFIERS_DIRECTORY = CONFIG_DIRECTORY + "custom_classifiers";
    public static final String FILTERS_DIRECTORY = CONFIG_DIRECTORY + "custom_filters";

    public static final BufferedImage APP_IMAGE;
    public static final BufferedImage ANT_ICON_IMAGE;
    public static final BufferedImage ANT_CONFUSED_ICON;
    public static final BufferedImage ANT_IMAGE;
    public static final BufferedImage EXCLAMATION_MARK_IMAGE;
    public static final BufferedImage DELETE_IMAGE;
    public static final BufferedImage SETTINGS_IMAGE;
    public static final BufferedImage CHART_IMAGE;
    public static BufferedImage DIFFING_IMAGE = null; // YET MISSING!
    public static final BufferedImage TABLE_IMAGE;
    public static final BufferedImage DETAILS_IMAGE;
    public static BufferedImage VISUALIZE_IMAGE = null; // YET MISSING!
    public static final BufferedImage DISABLE_MAGNIFIER_IMAGE;
    public static final BufferedImage BRUSH_IMAGE;
    public static final BufferedImage ARROW_IMAGE;
    public static final BufferedImage PLAY_IMAGE;
    public static final BufferedImage PAUSE_IMAGE;
    public static final BufferedImage TYPE_IMAGE;
    public static final BufferedImage EXCHANGE_IMAGE;
    public static final BufferedImage NODE_WITHOUT_ICON_IMAGE;
    public static final BufferedImage TOP_RIGHT_IMAGE;
    public static final BufferedImage HORIZONTAL_IMAGE;
    public static final BufferedImage DOT_IMAGE;
    public static final BufferedImage LIGHTBULB_ON_32_IMAGE;
    public static final BufferedImage LIGHTBULB_OFF_32_IMAGE;
    public static final BufferedImage LIGHTBULB_DISABLED_32_IMAGE;
    public static final BufferedImage EYE_IMAGE;
    public static final BufferedImage SHORT_LIVED_IMAGE;
    public static final BufferedImage DATA_STRUCTURE_IMAGE;
    public static final BufferedImage PERM_BORN_DIED_TEMP_IMAGE;
    public static final BufferedImage HEAP_TREND_IMAGE;
    public static final BufferedImage HEAP_EVOLUTION_IMAGE;
    public static final BufferedImage ROCKET_IMAGE;
    public static final BufferedImage GRAPH_IMAGE;
    public static final BufferedImage GROUP_IMAGE;
    public static final BufferedImage ZOOM_IN_IMAGE;
    public static final BufferedImage ZOOM_OUT_IMAGE;
    public static final BufferedImage RESET_ZOOM_IMAGE;
    public static final BufferedImage CONFIGURE_CHART_IMAGE;

    public static final ImageIcon APP_ICON;
    public static final ImageIcon LOADING_ICON;
    public static final ImageIcon EXCLAMATION_MARK_ICON;
    public static final ImageIcon DELETE_ICON;
    public static final ImageIcon CHART_ICON;
    public static ImageIcon DIFFING_ICON = null; // YET MISSING!
    public static final ImageIcon TABLE_ICON;
    public static final ImageIcon DETAILS_ICON;
    public static ImageIcon VISUALIZE_ICON = null; // YET MISSING!
    public static final ImageIcon DISABLE_MAGNIFIER_ICON;
    public static final ImageIcon BRUSH_ICON;
    public static final ImageIcon ARROW_ICON;
    public static final ImageIcon PLAY_ICON;
    public static final ImageIcon PAUSE_ICON;
    public static final ImageIcon TYPE_ICON;
    public static final ImageIcon EXCHANGE_ICON;
    public static final ImageIcon NODE_WITHOUT_ICON_ICON;
    public static final ImageIcon TOP_RIGHT_ICON;
    public static final ImageIcon HORIZONTAL_ICON;
    public static final ImageIcon DOT_ICON;
    public static final ImageIcon SHORT_LIVED_ICON;
    public static final ImageIcon DATA_STRUCTURE_ICON;
    public static final ImageIcon PERM_BORN_DIED_TEMP_ICON;
    public static final ImageIcon HEAP_TREND_ICON;
    public static final ImageIcon HEAP_EVOLUTION_ICON;
    public static final ImageIcon ROCKET_ICON;
    public static final ImageIcon GRAPH_ICON;
    public static final ImageIcon GROUP_ICON;
    public static final ImageIcon ZOOM_IN_ICON;
    public static final ImageIcon ZOOM_OUT_ICON;
    public static final ImageIcon RESET_ZOOM_ICON;
    public static final ImageIcon CONFIGURE_CHART_ICON;

    public static final ImagePack EXCHANGE_PACK;

    public static Color[] BASE_COLOR_VALUES;

    // http://www.mulinblog.com/a-color-palette-optimized-for-data-visualization/
    /*
     * 4D4D4D (gray) 5DA5DA (blue) -> darker #5394c4 -> darker #4a84ae -> 1 FAA43A (orange) 60BD68 (green) -> 3 F17CB0 (pink) B2912F (brown)
     * -> 5 B276B2 (purple) DECF3F (yellow) -> 4 F15854 (red) -> 2
     */
    static {
        float alpha = 0.8f;
        BASE_COLOR_VALUES = new Color[]{new Color(0x4A / 255.0f, 0x84 / 255.0f, 0xAE / 255.0f, alpha),
                                        new Color(0xF1 / 255.0f, 0x58 / 255.0f, 0x54 / 255.0f, alpha),
                                        new Color(0x60 / 255.0f, 0xBD / 255.0f, 0x68 / 255.0f, alpha),
                                        new Color(0xDE / 255.0f, 0xCF / 255.0f, 0x3F / 255.0f, alpha),
                                        new Color(0xB2 / 255.0f, 0x91 / 255.0f, 0x2F / 255.0f, alpha)};
    }

    public static final int DEFAULT_ICON_SIZE = 16;

    @SuppressWarnings("unused")
    private static final Logger LOGGER = Logger.getLogger(Consts.class.getSimpleName());

    static {
        APP_IMAGE = ImageUtil.loadResourceImage("App", "app.png");
        ANT_ICON_IMAGE = ImageUtil.loadResourceImage("Ant Icon", "ant_icon.jpg");
        ANT_CONFUSED_ICON = ImageUtil.loadResourceImage("Ant Confused Icon", "ant_confused.jpg");
        ANT_IMAGE = ImageUtil.loadResourceImage("Ant", "ant_tracks.jpg");
        EXCLAMATION_MARK_IMAGE = ImageUtil.loadResourceImage("Exclamation mark", "exclamationMark.png");
        DELETE_IMAGE = ImageUtil.loadResourceImage("Clear", "delete.png");
        SETTINGS_IMAGE = ImageUtil.loadResourceImage("Configure", "wrench.png");
        CHART_IMAGE = ImageUtil.loadResourceImage("Chart", "chart.png");
        TABLE_IMAGE = ImageUtil.loadResourceImage("Statistics", "statistics.png");
        DETAILS_IMAGE = ImageUtil.loadResourceImage("Details", "details.png");
        DISABLE_MAGNIFIER_IMAGE = ImageUtil.loadResourceImage("Disable Magnifier", "disableMagnifier.png");
        BRUSH_IMAGE = ImageUtil.loadResourceImage("Brush", "brush.png");
        ARROW_IMAGE = ImageUtil.loadResourceImage("Arrow", "arrow.png");
        PLAY_IMAGE = ImageUtil.loadResourceImage("Play", "play.png");
        PAUSE_IMAGE = ImageUtil.loadResourceImage("Pause", "pause.png");
        TYPE_IMAGE = ImageUtil.loadResourceImage("Type", "type.png");
        EXCHANGE_IMAGE = ImageUtil.loadResourceImage("Exchange Arrows", "exchangearrows.png");
        NODE_WITHOUT_ICON_IMAGE = ImageUtil.loadResourceImage("Empty icon", "nodeWithoutIcon.png");
        TOP_RIGHT_IMAGE = ImageUtil.loadResourceImage("Top-right line", "topright.png");
        HORIZONTAL_IMAGE = ImageUtil.loadResourceImage("Horizontal line", "horizontal.png");
        DOT_IMAGE = ImageUtil.loadResourceImage("Dot", "dot.png");
        LIGHTBULB_ON_32_IMAGE = ImageUtil.loadResourceImage("Lightbulb on 32", "lightbulb_on_32.png");
        LIGHTBULB_OFF_32_IMAGE = ImageUtil.loadResourceImage("Lightbulb off 32", "lightbulb_off_32.png");
        LIGHTBULB_DISABLED_32_IMAGE = ImageUtil.loadResourceImage("Lightbulb disabled 32", "lightbulb_disabled_32.png");
        EYE_IMAGE = ImageUtil.loadResourceImage("Eye", "eye.png");
        SHORT_LIVED_IMAGE = ImageUtil.loadResourceImage("Short Lived", "shortlived.png");
        DATA_STRUCTURE_IMAGE = ImageUtil.loadResourceImage("Data structure", "datastructure.png");
        PERM_BORN_DIED_TEMP_IMAGE = ImageUtil.loadResourceImage("Perm Born Died Temp", "pbdt.png");
        HEAP_TREND_IMAGE = ImageUtil.loadResourceImage("Object Group Trend", "trend.png");
        HEAP_EVOLUTION_IMAGE = ImageUtil.loadResourceImage("Heap Evolution", "evolution.png");
        ROCKET_IMAGE = ImageUtil.loadResourceImage("Rocket", "rocket.png");
        GRAPH_IMAGE = ImageUtil.loadResourceImage("Graph", "graph.png");
        GROUP_IMAGE = ImageUtil.loadResourceImage("Group", "group.png");
        ZOOM_IN_IMAGE = ImageUtil.loadResourceImage("Zoom in", "zoomIn.png");
        ZOOM_OUT_IMAGE = ImageUtil.loadResourceImage("Zoom out", "zoomOut.png");
        RESET_ZOOM_IMAGE = ImageUtil.loadResourceImage("Reset zoom", "resetZoom.png");
        CONFIGURE_CHART_IMAGE = ImageUtil.loadResourceImage("Configure chart", "configureChart.png");

        APP_ICON = ImageUtil.asIcon(APP_IMAGE);
        LOADING_ICON = new ImageIcon("./resources/img/loader.gif");
        EXCLAMATION_MARK_ICON = ImageUtil.asIcon(EXCLAMATION_MARK_IMAGE);
        DELETE_ICON = ImageUtil.asIcon(DELETE_IMAGE);
        CHART_ICON = ImageUtil.asIcon(CHART_IMAGE);
        TABLE_ICON = ImageUtil.asIcon(TABLE_IMAGE);
        DETAILS_ICON = ImageUtil.asIcon(DETAILS_IMAGE);
        DISABLE_MAGNIFIER_ICON = ImageUtil.asIcon(DISABLE_MAGNIFIER_IMAGE);
        BRUSH_ICON = ImageUtil.asIcon(BRUSH_IMAGE);
        ARROW_ICON = ImageUtil.asIcon(ARROW_IMAGE);
        PLAY_ICON = ImageUtil.asIcon(PLAY_IMAGE);
        PAUSE_ICON = ImageUtil.asIcon(PAUSE_IMAGE);
        TYPE_ICON = ImageUtil.asIcon(TYPE_IMAGE);
        EXCHANGE_ICON = ImageUtil.asIcon(EXCHANGE_IMAGE);
        NODE_WITHOUT_ICON_ICON = ImageUtil.asIcon(NODE_WITHOUT_ICON_IMAGE);
        TOP_RIGHT_ICON = ImageUtil.asIcon(TOP_RIGHT_IMAGE);
        HORIZONTAL_ICON = ImageUtil.asIcon(HORIZONTAL_IMAGE);
        DOT_ICON = ImageUtil.asIcon(DOT_IMAGE);
        SHORT_LIVED_ICON = ImageUtil.asIcon(SHORT_LIVED_IMAGE);
        DATA_STRUCTURE_ICON = ImageUtil.asIcon(DATA_STRUCTURE_IMAGE);
        PERM_BORN_DIED_TEMP_ICON = ImageUtil.asIcon(PERM_BORN_DIED_TEMP_IMAGE);
        HEAP_TREND_ICON = ImageUtil.asIcon(HEAP_TREND_IMAGE);
        HEAP_EVOLUTION_ICON = ImageUtil.asIcon(HEAP_EVOLUTION_IMAGE);
        ROCKET_ICON = ImageUtil.asIcon(ROCKET_IMAGE);
        GRAPH_ICON = ImageUtil.asIcon(GRAPH_IMAGE);
        GROUP_ICON = ImageUtil.asIcon(GROUP_IMAGE);
        ZOOM_IN_ICON = ImageUtil.asIcon(ZOOM_IN_IMAGE);
        ZOOM_OUT_ICON = ImageUtil.asIcon(ZOOM_OUT_IMAGE);
        RESET_ZOOM_ICON = ImageUtil.asIcon(RESET_ZOOM_IMAGE);
        CONFIGURE_CHART_ICON = ImageUtil.asIcon(CONFIGURE_CHART_IMAGE);

        EXCHANGE_PACK = new ImagePack() {
            @Override
            public ImageView getAsNewNode() {
                return ImageUtil.getIconNode(EXCHANGE_ICON);
            }

            @Override
            public BufferedImage getImage() {
                return EXCHANGE_IMAGE;
            }

            @Override
            public ImageIcon getIcon() {
                return EXCHANGE_ICON;
            }
        };

        // try {
        // img = ImageIO.read(new File("./resources/img/diff.png"));
        // DIFFING_ICON = new ImageIcon(img.getScaledInstance(ICON_SIZE,
        // ICON_SIZE, Image.SCALE_SMOOTH));
        // } catch (Exception e) {
        // LOGGER.warning("Could not load \"Diffing\" icon\n" + e);
        // }
        // try {
        // img = ImageIO.read(new File("./resources/img/visualize.png"));
        // VISUALIZE_ICON = new ImageIcon(img.getScaledInstance(ICON_SIZE,
        // ICON_SIZE, Image.SCALE_SMOOTH));
        // } catch (Exception e) {
        // LOGGER.warning("Could not load \"Visualize\" icon\n" + e);
        // }
    }
}
