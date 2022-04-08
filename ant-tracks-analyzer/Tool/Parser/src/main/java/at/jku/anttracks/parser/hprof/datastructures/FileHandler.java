/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package at.jku.anttracks.parser.hprof.datastructures;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;

/**
 * Static class that allows reading and writing from and to files
 *
 * @author manuel
 */
public final class FileHandler {

    private FileHandler() {
    }

    /**
     * Reads all lines from a File
     *
     * @param path pathname for the File
     * @return A string array, where each field contains a line of the file
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static String[] readAllLines(String path) throws FileNotFoundException, IOException {
        LinkedList<String> output = new LinkedList<>();
        String buffer;
        int counter = 0;
        try(BufferedReader reader = new BufferedReader(new java.io.FileReader(path))){
	        while ((buffer = reader.readLine()) != null) {
	            output.add(buffer);
	            counter++;
	        }
	        String[] arrayOutput = new String[counter];
	        counter = 0;
	        for (String string : output) {
	            arrayOutput[counter] = string;
	            counter++;
	        } 
	        return arrayOutput;
        }
       
    }

    /**
     * Reads the text out of a file. New lines are seperated by nothing.
     *
     * @param path Path of the file.
     * @return A string containing the text.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static String readAllText(String path) throws FileNotFoundException, IOException {
        return readAllTextWithSeperator(path, "");
    }

    /**
     * Reads all the text of a file. New lines are sperated by given seperator.
     *
     * @param path The path of the file.
     * @param seperator String that seperates the lines.
     * @return String containing the content of the file.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public static String readAllTextWithSeperator(String path, String seperator) throws FileNotFoundException, IOException {
        StringBuilder output = new StringBuilder();
        try(BufferedReader reader = new BufferedReader(new java.io.FileReader(path))){
	        String tmp;
	        while (((tmp = reader.readLine()) != null)) {
	            output.append(tmp).append(seperator);
	        }
	        return output.toString();
        }
    }

    /**
     * Returns the permissions of the file for the program
     *
     * @param path
     * @return a string, with characters in the following pattern: (r)ead,
     * (w)rite, e(x)ecute.
     * @throws FileNotFoundException
     */
    public static String getFilePermissions(String path) throws FileNotFoundException {
        java.io.File file = new java.io.File(path);
        if (!file.exists()) {
            throw new FileNotFoundException();
        }
        String permissions = "";
        if (file.canRead()) {
            permissions += 'r';
        } else {
            permissions += '-';
        }
        if (file.canWrite()) {
            permissions += 'w';
        } else {
            permissions += '-';
        }
        if (file.canExecute()) {
            permissions += 'x';
        } else {
            permissions += '-';
        }
        return permissions;
    }

    /**
     * Writes a string-Array for line into a file If there is an old file, it
     * will be overwritten If there is no file with the specific name, a new
     * file will be created
     *
     * @param path Path of the file.
     * @param lines Array of lines for writing.
     * @throws IOException
     */
    public static void writeAllLines(String path, String lines) throws IOException {
        writeAllLines(path, lines, true, false);
    }

    /**
     * Writes a string-Array for line into a file If there is an old file, it
     * will be overwritten If there is no file with the specific name, a new
     * file will be created
     *
     * @param path Path of the file.
     * @param lines Array of lines for writing.
     * @param override if the existing file should be overwritten.
     * @throws IOException
     */
    public static void writeAllLines(String path, String lines, boolean override, boolean newline) throws IOException {
        BufferedWriter writer = new BufferedWriter(new java.io.FileWriter(path, !override));
        java.io.File file = new java.io.File(path);
        if (!file.exists()) {
            file.createNewFile();
        }
        if (file.exists() && override) {
            file.delete();
            file.createNewFile();
        }
        writer.write(lines, 0, lines.length());
        if (newline) writer.write("\n");
        writer.flush();
        writer.close();
    }
}
