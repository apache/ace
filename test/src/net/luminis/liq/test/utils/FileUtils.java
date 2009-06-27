package net.luminis.liq.test.utils;

import java.io.File;
import java.io.IOException;

public class FileUtils {

    /**
     * Convenience method for creating temp files.
     * It creates a temp file, and then deletes it. This is done so the same (unique) filename can be used to create a directory.
     *
     * If you use null as the baseDirectoryName, a tempfile is created in the platform specific temp directory.
     * @throws IOException
     */
    public static File createTempFile(File baseDirectory) throws IOException {
        return createTempFile(baseDirectory, "");
    }

    public static File createTempFile(File baseDirectory, String extension) throws IOException {
        File tempFile = File.createTempFile("test", extension, baseDirectory);
        tempFile.delete();
        return tempFile;
    }

    /**
     * Remove the given directory and all it's files and subdirectories
     * @param directory the name of the directory to remove
     */
    public static void removeDirectoryWithContent(File directory) {
        if ((directory == null) || !directory.exists()) {
            return;
        }
        File[] filesAndSubDirs = directory.listFiles();
        for (int i=0; i < filesAndSubDirs.length; i++) {
            File file = filesAndSubDirs[i];
            if (file.isDirectory()) {
                removeDirectoryWithContent(file);
            }
            // else just remove the file
            file.delete();
        }
        directory.delete();
    }

}
