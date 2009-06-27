package net.luminis.liq.client.repository.impl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import net.luminis.liq.repository.impl.FilebasedBackupRepository;
import net.luminis.liq.test.utils.TestUtils;

import org.testng.annotations.Test;

public class FilebasedBackupRepositoryTest {

    /**
     * A basic scenario: we write, backup, write again, and revert.
     */
    @Test( groups = { TestUtils.UNIT } )
    public void testFilebasedBackupRepository() throws IOException {
        File current = File.createTempFile("testFilebasedBackupRepository", null);
        File backup = File.createTempFile("testFilebasedBackupRepository", null);
        current.deleteOnExit();
        backup.deleteOnExit();

        FilebasedBackupRepository rep = new FilebasedBackupRepository(current, backup);

        byte[] testContent = new byte[] {'i', 'n', 'i', 't', 'i', 'a', 'l'};

        // write initial content
        rep.write(new ByteArrayInputStream(testContent));

        // read initial content
        InputStream input = rep.read();
        byte[] inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, testContent) : "We got something different than 'initial' from read: " + new String(inputBytes);

        // backup what's in the repository
        rep.backup();

        // write new content
        byte[] newTestContent = new byte[] {'n', 'e', 'w'};
        rep.write(new ByteArrayInputStream(newTestContent));

        // read current content
        input = rep.read();
        inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, newTestContent) : "We got something different than 'new' from read: " + new String(inputBytes);

        // revert to previous (initial) content
        rep.restore();

        // read current content
        input = rep.read();
        inputBytes = AdminTestUtil.copy(input);
        assert AdminTestUtil.byteArraysEqual(inputBytes, testContent) : "We got something different than 'initial' from read: " + new String(inputBytes);
    }

}
