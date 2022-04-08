package at.jku.anttracks.util;

import javafx.scene.image.ImageView;

import javax.swing.*;
import java.awt.image.BufferedImage;

public interface ImagePack {
    ImageView getAsNewNode();

    BufferedImage getImage();

    ImageIcon getIcon();
}
