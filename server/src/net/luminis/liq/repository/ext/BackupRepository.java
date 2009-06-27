package net.luminis.liq.repository.ext;

import java.io.IOException;
import java.io.InputStream;

/**
 * Provides an interface for backing up objects. <code>write</code> and <code>read</code>
 * allow writing and reading of the current version of the object. <code>backup</code>
 * backs up the object, and <code>restore</code> restores it from a previously backed up
 * version, if any. There is no way to directly use the backup.
 */
public interface BackupRepository {

    /**
     * Writes the input stream to the current object.
     * @param data The data to be written. Remember to close this stream, if necessary.
     * @throws IOException Will be thrown when (a) the input stream gets closed
     * unexpectedly, or (b) there is an error writing the data.
     */
    public void write(InputStream data) throws IOException;

    /**
     * Reads the input stream from the current object. If there is no current version,
     * an empty stream will be returned.
     * @return An input stream, from which can be read. Remember to close it.
     * @throws IOException Will be thrown when there is a problem storing the data.
     */
    public InputStream read() throws IOException;

    /**
     * Restores a previously backuped version of the object.
     * @return True when there was a previously backup version which has
     * now been restored, false otherwise.
     * @throws IOException Thrown when the restore process goes bad.
     */
    public boolean restore() throws IOException;

    /**
     * Backs up the current version of the object, overwriting a previous
     * backup, if any.
     * @return True when there was a current version to be backed up, false
     * otherwise.
     * @throws IOException Thrown when the restore process goes bad.
     */
    public boolean backup() throws IOException;

}
