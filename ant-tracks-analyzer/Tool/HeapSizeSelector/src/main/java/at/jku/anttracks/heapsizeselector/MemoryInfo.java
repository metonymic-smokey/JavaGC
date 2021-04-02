package at.jku.anttracks.heapsizeselector;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

public class MemoryInfo {

    public static final long MULTIPLIER = 1024;
    public static final String[] UNITS = new String[]{"B", "KB", "MB", "GB", "TB"};

    public final long total;
    public final double available;

    private MemoryInfo(long total, double available) {
        this.total = total;
        this.available = available;
    }

    public static MemoryInfo get() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("linux")) {
            long total = 0;
            long available = 0;
            try (BufferedReader in = new BufferedReader(new FileReader("/proc/meminfo"))) {
                for (String line = in.readLine(); line != null; line = in.readLine()) {
                    String[] tokens = Arrays.stream(line.replace(":", "").split(" ")).filter(t -> t.length() > 0).toArray(String[]::new);
                    if (tokens.length != 3) {
                        continue;
                    }
                    String name = tokens[0];
                    long value = Long.parseLong(tokens[1]);
                    String units = tokens[2];
                    switch (name) {
                        case "MemTotal":
                            total = toBytes(value, units);
                            break;
                        case "MemAvailable":
                            available = toBytes(value, units);
                            break;
                        default:
                            break;
                    }
                }
                return new MemoryInfo(total, 1.0 * available / total);
            } catch (IOException ioe) {
                ioe.printStackTrace(System.err);
                return null;
            }
        } else if (os.contains("windows")) {
            long total;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(Runtime.getRuntime()
                                                                                     .exec("wmic ComputerSystem get TotalPhysicalMemory")
                                                                                     .getInputStream()))) {
                in.readLine(); //skip header
                in.readLine(); //and skip \r
                String value = in.readLine().trim();
                total = Long.parseLong(value);
            } catch (IOException e) {
                e.printStackTrace(System.err);
                return null;
            }
            long available;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("wmic OS get FreePhysicalMemory").getInputStream()))) {
                in.readLine(); //skip header
                in.readLine(); //and skip \r
                String value = in.readLine().trim();
                available = Long.parseLong(value) * MULTIPLIER;
            } catch (IOException e) {
                e.printStackTrace(System.err);
                return null;
            }
            return new MemoryInfo(total, 1.0 * available / total);
        } else {
            System.err.println("Operating System '" + os + "' not supported.");
            return null;
        }
    }

    @SuppressWarnings("fallthrough")
    private static long toBytes(long value, String units) {
        switch (units.toLowerCase()) {
            case "tb":
                value *= MULTIPLIER;
            case "gb":
                value *= MULTIPLIER;
            case "mb":
                value *= MULTIPLIER;
            case "kb":
                value *= MULTIPLIER;
            case "b":
        }
        return value;
    }

    public static String toString(double bytes) {
        for (int i = 0; i < UNITS.length; i++) {
            if (bytes < MULTIPLIER) {
                return String.format("%.2f %s", bytes, UNITS[i]);
            }
            bytes /= MULTIPLIER;
        }
        return "too many";
    }

}