package net.luminis.test.build;

import java.util.Collection;

import net.luminis.liq.test.utils.TestUtils;

import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.Reporter;
import org.testng.TestListenerAdapter;

/**
 * Reports information about broken tests.
 */
public class BrokenTestListener extends TestListenerAdapter {

    @Override
    public void onStart(ITestContext testContext) {
        Collection<ITestNGMethod> excluded = testContext.getExcludedMethods();
        StringBuffer output = new StringBuffer();
        output.append("<h2>Broken tests</h2>\n");
        output.append("<table><tr><td>Name</td><td>Class</td><td>Groups</td><td>Description</td></tr>");
        for (ITestNGMethod m : excluded) {
            StringBuffer groups = new StringBuffer();
            boolean found = false;
            for (String g : m.getGroups()) {
                if (TestUtils.BROKEN.equals(g)) {
                    found = true;
                }
                else {
                    if (groups.length() > 0) {
                        groups.append(", ");
                    }
                    groups.append(g);
                }
            }
            if (found) {
                output.append("<tr>" +
            		"<td>" + m.getMethodName() + "</td>" +
    				"<td>" + m.getMethod().getDeclaringClass().getName() + "</td>" +
                    "<td>" + groups.toString() + "</td>" +
                    "<td>" + m.getDescription() + "</td>" +
					"</tr>\n");
            }
        }
        output.append("</table>\n");
        Reporter.log(output.toString());
    }
}
