package net.luminis.liq.resourceprocessor.useradmin;

import java.io.IOException;
import java.io.InputStream;

import org.w3c.dom.Document;

/**
 * The UserAdminConfigurator can be used to install, remove or explicitly set the users that
 * should be present in the system's UserAdmin.<br>
 * <br>
 * The document should have the following shape,
 * <pre>
 * &lt;roles&gt;
 *     &lt;group name="group1"/&gt;
 *     &lt;group name="group2"&gt;
 *         &lt;memberof&gt;group1&lt;/memberof&gt;
 *     &lt;/group&gt;
 *     &lt;user name="user1"&gt;
 *         &lt;properties&gt;
 *             &lt;realname type="String"&gt;Mr. U. One&lt;/realname&gt;
 *             &lt;address&gt;1 Infinite Loop&lt;/realname&gt;
 *         &lt;/properties&gt;
 *         &lt;credentials&gt;
 *             &lt;password type="byte[]"&gt;secret&lt;/password&gt;
 *         &lt;/credentials&gt;
 *         &lt;memberof&gt;group1&lt;/memberof&gt;
 *     &lt;/user&gt;
 * &lt;/roles&gt;
 * </pre>
 * Note that when 'type' is missing in the values for properties or credentials, "String" will be assumed.
 * <br>
 * When no UserAdmin is available at time of installation, the UserAdminStore will keep the
 * data around until one is, and update it with all data it has received up to then.
 * Note that UserAdminStore is intended to work with one UserAdmin at a time.
 */
public interface UserAdminConfigurator {
    /**
     * Installs all roles found in a document.
     * @param doc The document.
     */
    public void install(Document doc);
    /**
     * Installs all roles found in a document.
     * @param input A stream containing the document.
     * @throws IOException When there is a problem retrieving the document from the stream.
     */
    public void install(InputStream input) throws IOException;
    /**
     * Removes all roles found in a document.
     * @param doc The document.
     */
    public void uninstall(Document doc);
    /**
     * Uninstalls all roles found in a document.
     * @param input A stream containing the document.
     * @throws IOException When there is a problem retrieving the document from the stream.
     */
    public void uninstall(InputStream input) throws IOException;

    /**
     * Sets the users found in a document as the only users to be present
     * in the UserAdmin.
     * @param doc The document.
     */
    public void setUsers(Document doc);
    /**
     * Sets the users found in a document as the only users to be present
     * in the UserAdmin.
     * @param input A stream containing the document.
     * @throws IOException When there is a problem retrieving the document from the stream.
     */
    public void setUsers(InputStream input) throws IOException;
}
