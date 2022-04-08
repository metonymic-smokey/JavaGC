
package at.jku.anttracks.gui.utils;

public class OsScript {

    public static boolean isSupported() {
        return isLinux() || isWindows();
    }

    public static String getScriptExecutable() {
        if (isLinux()) {
            return "/bin/bash";
        }
        if (isWindows()) {
            return "cmd.exe";
        }
        return null;
    }

    public static String getScriptExtension() {
        if (isLinux()) {
            return "sh";
        }
        if (isWindows()) {
            return "bat";
        }
        return null;
    }

    private static boolean isLinux() {
        return getOperatingSystem().contains("Linux");
    }

    private static boolean isWindows() {
        return getOperatingSystem().contains("Windows");
    }

    private static String getOperatingSystem() {
        return System.getProperty("os.name");
    }

}
