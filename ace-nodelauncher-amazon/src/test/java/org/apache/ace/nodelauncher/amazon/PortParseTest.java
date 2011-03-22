package org.apache.ace.nodelauncher.amazon;

import org.testng.Assert;
import org.testng.annotations.Test;
import static org.apache.ace.test.utils.TestUtils.UNIT;

public class PortParseTest {
    @Test(groups = { UNIT })
    public void testParsePortsFromString() throws Exception {
        AmazonNodeLauncher instance = new AmazonNodeLauncher();
        Assert.assertEquals(instance.parseExtraPorts("1,2,3"), new int[] {1, 2, 3});
        Assert.assertEquals(instance.parseExtraPorts(""), new int[] {});
        Assert.assertEquals(instance.parseExtraPorts("1 ,2 , 3 "), new int[] {1, 2, 3});
        Assert.assertEquals(instance.parseExtraPorts("800,900"), new int[] {800, 900});
    }
    
    @Test(groups = { UNIT })
    public void testMergePorts() throws Exception {
        AmazonNodeLauncher instance = new AmazonNodeLauncher();
        Assert.assertEquals(instance.mergePorts(new int[] {1, 2}, new int[] {3, 4, 5}), new int[] {1, 2, 3, 4, 5});
        Assert.assertEquals(instance.mergePorts(new int[] {1}, new int[] {}), new int[] {1});
        Assert.assertEquals(instance.mergePorts(new int[] {}, new int[] {}), new int[] {});
        Assert.assertEquals(instance.mergePorts(new int[] {}, new int[] {1, 2, 3}), new int[] {1, 2, 3});
    }
}
