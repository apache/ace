package net.luminis.liq.obr.storage;

import java.io.IOException;
import java.io.InputStream;

import org.osgi.service.cm.ManagedService;

public interface BundleStore extends ManagedService {

    /**
     * Returns an <code>InputStream</code> to the data of the specified resource.
     *
     * @param fileName Identifier of the requested resource.
     * @return <code>InputStream</code> to the requested resource or <code>null</code> if no such resource is available.
     * @throws IOException If there was a problem returning the requested resource.
     */
    public InputStream get(String fileName) throws IOException;

    /**
     * Stores the specified resource in the store.
     *
     * @param fileName Identifier of the resource.
     * @param data The actual data of the resource.
     * @return <code>true</code> if the resource was successfully stored, <code>false</code> if the resource already existed
     * @throws IOException If there was a problem reading or writing the data of the resource.
     */
    public boolean put(String fileName, InputStream data) throws IOException;

    /**
     * Removes the specified resource from the store.
     *
     * @param filename Identifier of the resource.
     * @return <code>true</code> if the resource was successfully removed, <code>false</code> if the resource was not present to begin with
     * @throws IOException If there was a problem removing the data of the resource from the store.
     */
    public boolean remove(String filename) throws IOException;
}
