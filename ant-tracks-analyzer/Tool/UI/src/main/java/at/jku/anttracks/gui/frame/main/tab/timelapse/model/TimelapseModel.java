
package at.jku.anttracks.gui.frame.main.tab.timelapse.model;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Christina Rammerstorfer
 */
public class TimelapseModel {
    private Map<Long, BufferedImage> images;
    private Long currentSelection;

    public TimelapseModel() {
        images = new HashMap<>();
    }

    public void addImage(long time, BufferedImage image) {
        images.put(time, image);
    }

    public Long[] getTimes() {
        Long[] times = images.keySet().toArray(new Long[0]);
        Arrays.sort(times);
        return times;
    }

    //	public void setCurrentSelection(int timeIndex){
    //		Long[] times = images.keySet().toArray(new Long[0]);
    //		Arrays.sort(times);
    //		currentSelection = times[timeIndex];
    //	}

    public void setCurrentSelection(long time) {
        currentSelection = time;
    }

    public long getCurrentSelection() {
        return currentSelection;
    }

    public BufferedImage getCurrentImage() {
        return images.get(currentSelection);
    }

    public long getNextValue(long curValue) {
        Long[] times = images.keySet().toArray(new Long[0]);
        Arrays.sort(times);
        for (int i = 0; i < times.length; i++) {
            if (times[i] == curValue) {
                return times[i + 1];
            }
        }
        return -1;
    }

    public boolean containsKey(long value) {
        return images.containsKey(value);
    }

    public long getClosestValue(long value) {
        Long[] times = images.keySet().toArray(new Long[0]);
        Arrays.sort(times);
        long minDiff = Long.MAX_VALUE;
        int minIndex = 0;
        for (int i = 0; i < times.length; i++) {
            long timeDiff = Math.abs(value - times[i]);
            if (timeDiff < minDiff) {
                minDiff = timeDiff;
                minIndex = i;
            }
        }
        return times[minIndex];
    }

}
