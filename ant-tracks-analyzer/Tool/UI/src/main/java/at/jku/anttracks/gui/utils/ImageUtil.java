
package at.jku.anttracks.gui.utils;

import at.jku.anttracks.util.ImagePack;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.util.logging.Logger;

public class ImageUtil {
    private static final Logger LOGGER = Logger.getLogger(ImageUtil.class.getSimpleName());

    public static BufferedImage loadResourceImage(String name, String fileName) {
        return loadImage(name, "/images/" + fileName);
    }

    public static BufferedImage loadImage(String name, String path) {
        try {
            return ImageIO.read(ImageUtil.class.getResourceAsStream(path));
        } catch (Exception e) {
            try {
                return ImageIO.read(new FileInputStream("./resources/" + path));
            } catch (Throwable t) {
                LOGGER.warning("Could not load \"" + name + "\" image from " + path + "\n" + e);
                return null;
            }
        }
    }

    public static ImagePack getResourceImagePack(String name, String fileName) {
        return new ImagePack() {
            BufferedImage image = ImageUtil.loadResourceImage(name, fileName);
            ImageIcon icon = ImageUtil.loadResourceIcon(name, fileName);

            @Override
            public ImageView getAsNewNode() {
                ImageView ret = ImageUtil.getIconNode(icon, Consts.DEFAULT_ICON_SIZE, Consts.DEFAULT_ICON_SIZE);
                ret.setAccessibleText(name);
                return ret;
            }

            @Override
            public BufferedImage getImage() {
                return image;
            }

            @Override
            public ImageIcon getIcon() {
                return icon;
            }
        };
    }

    public static ImageIcon loadResourceIcon(String name, String fileName) {
        return asIcon(loadResourceImage(name, fileName));
    }

    public static ImageIcon loadIcon(String name, String path) {
        return asIcon(loadImage(name, path));
    }

    public static ImageIcon asIcon(BufferedImage image) {
        if (image == null) {
            return null;
        }
        return asIcon(image, Consts.DEFAULT_ICON_SIZE);
    }

    public static ImageIcon asIcon(BufferedImage image, int iconSize) {
        return new ImageIcon(image.getScaledInstance(iconSize, iconSize, Image.SCALE_SMOOTH));
    }

    public static ImageView getIconNode(BufferedImage image, int width, int height) {
        ImageView iconNode = new ImageView(SwingFXUtils.toFXImage(image, null));
        iconNode.setFitWidth(width);
        iconNode.setFitHeight(height);
        return iconNode;
    }

    public static ImageView getIconNode(BufferedImage image) {
        return getIconNode(image, Consts.DEFAULT_ICON_SIZE, Consts.DEFAULT_ICON_SIZE);
    }

    public static ImageView getIconNode(Icon imageIcon, int width, int height) {
        BufferedImage image = new BufferedImage(imageIcon.getIconWidth(), imageIcon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.createGraphics();
        imageIcon.paintIcon(null, g, 0, 0);
        g.dispose();
        return getIconNode(image, width, height);
    }

    public static ImageView getIconNode(Icon imageIcon) {
        return getIconNode(imageIcon, Consts.DEFAULT_ICON_SIZE, Consts.DEFAULT_ICON_SIZE);
    }

    public static BufferedImage invert(BufferedImage input) {
        int w = input.getWidth();
        int h = input.getHeight();

        BufferedImage newImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int wi = 0; wi < w; wi++) {
            for (int hi = 0; hi < h; hi++) {
                Color fromColor = new Color(input.getRGB(wi, hi));
                Color toColor = new Color(255 - fromColor.getRed(),
                                          255 - fromColor.getGreen(),
                                          255 - fromColor.getBlue(),
                                          fromColor.getAlpha());
                newImage.setRGB(wi, hi, toColor.getRGB());
            }
        }
        return newImage;
    }

    public static javafx.scene.image.Image toFXImage(BufferedImage image) {
        WritableImage fxImage = new WritableImage(image.getWidth(), image.getHeight());
        SwingFXUtils.toFXImage(image, fxImage);
        return fxImage;
    }
}
