package org.apache.ace.nodelauncher.amazon;

import org.apache.ace.nodelauncher.amazon.impl.AmazonNodeLauncher;
import org.testng.Assert;
import org.testng.annotations.Test;
import static org.apache.ace.test.utils.TestUtils.UNIT;

public class PortParseTest {
    @Test(groups = { UNIT })
    public void testParsePortsFromString() throws Exception {
        AmazonNodeLauncher instance = new AmazonNodeLauncher();
        Assert.assertTrue(assertEquals(instance.parseExtraPorts(new String[] {"1", "2", "3"}), new int[] {1, 2, 3}));
        Assert.assertTrue(assertEquals(instance.parseExtraPorts(new String[] {}), new int[] {}));
        Assert.assertTrue(assertEquals(instance.parseExtraPorts(new String[] {"800","900"}), new int[] {800, 900}));
    }
    
    @Test(groups = { UNIT })
    public void testMergePorts() throws Exception {
        AmazonNodeLauncher instance = new AmazonNodeLauncher();
        Assert.assertTrue(assertEquals(instance.mergePorts(new int[] {1, 2}, new int[] {3, 4, 5}), new int[] {1, 2, 3, 4, 5}));
        Assert.assertTrue(assertEquals(instance.mergePorts(new int[] {1}, new int[] {}), new int[] {1}));
        Assert.assertTrue(assertEquals(instance.mergePorts(new int[] {}, new int[] {}), new int[] {}));
        Assert.assertTrue(assertEquals(instance.mergePorts(new int[] {}, new int[] {1, 2, 3}), new int[] {1, 2, 3}));
    }
    
    private boolean assertEquals(int[] a, int[] b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }
}
