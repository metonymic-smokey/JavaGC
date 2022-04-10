package at.jku.anttracks.util;

public interface ProgressListener {
    void fire(double progress, String newMessage);
}
