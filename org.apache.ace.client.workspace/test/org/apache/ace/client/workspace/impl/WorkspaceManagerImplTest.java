package org.apache.ace.client.workspace.impl;

import static org.apache.ace.test.utils.TestUtils.UNIT;

import java.util.Properties;

import org.apache.ace.client.workspace.impl.WorkspaceManagerImpl;
import org.testng.Assert;
import org.testng.annotations.Test;

public class WorkspaceManagerImplTest {
    @SuppressWarnings("serial")
	@Test(groups = { UNIT })
    public void testPropertyGetter() {
        WorkspaceManagerImpl s = new WorkspaceManagerImpl();
        Assert.assertEquals(s.getProperty(new Properties() {{ put("key", "value"); }},  "key", "notused"), "value");
        Assert.assertEquals(s.getProperty(new Properties() {{ put("unusedkey", "value"); }},  "key", "default"), "default");
        Assert.assertEquals(s.getProperty(null,  "key", "default"), "default");
    }
}
