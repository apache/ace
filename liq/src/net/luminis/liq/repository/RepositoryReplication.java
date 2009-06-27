package net.luminis.liq.repository;

import java.io.IOException;
import java.io.InputStream;

/**
 * The interface for replication of the data in a repository.
 */
public interface RepositoryReplication {

    /**
     * Determines the versions inside the repository.
     * 
     * @returns A <code>SortedRangeSet</code> representing all the versions currently inside the repository.
     * @throws IOException If there is an error determining the current versions.
     */
    public SortedRangeSet getRange() throws IOException;
    
    /**
     * Gets the specified version.
     * 
     * @return A stream containing the specified version's data or <code>null</code> if the version does not exist.
     * @throws IOException If there is an error reading the version.
     * @throws IllegalArgumentException If the specified version is not greater than 0.
     */
    public InputStream get(long version) throws IOException, IllegalArgumentException;

    /**
     * Store the stream data as the specified version.
     * 
     * @return returns True if all went fine, false if the version already existed.
     * @throws IOException If the stream data could not be stored successfully due to I/O problems.
     * @throws IllegalArgumentException If the version number is not greater than 0.
     */
    public boolean put(InputStream data, long version) throws IOException, IllegalArgumentException;
}
