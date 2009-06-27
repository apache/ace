package net.luminis.liq.client.repository.impl;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import net.luminis.liq.client.repository.ObjectRepository;
import net.luminis.liq.client.repository.RepositoryAdmin;
import net.luminis.liq.client.repository.RepositoryAdminLoginContext;
import net.luminis.liq.client.repository.RepositoryObject;
import net.luminis.liq.client.repository.RepositoryObject.WorkingState;
import net.luminis.liq.client.repository.impl.RepositoryAdminLoginContextImpl.RepositorySetDescriptor;
import net.luminis.liq.client.repository.repository.ArtifactRepository;
import net.luminis.liq.repository.Repository;
import net.luminis.liq.repository.ext.BackupRepository;
import net.luminis.liq.repository.ext.CachedRepository;
import net.luminis.liq.repository.impl.CachedRepositoryImpl;
import net.luminis.liq.repository.impl.FilebasedBackupRepository;
import net.luminis.liq.repository.impl.RemoteRepository;

import org.osgi.framework.BundleContext;
import org.osgi.service.log.LogService;
import org.osgi.service.prefs.Preferences;
import org.osgi.service.prefs.PreferencesService;
import org.osgi.service.useradmin.User;

/**
 * An implementation of RepositoryAdmin, responsible for managing <code>ObjectRepositoryImpl</code>
 * descendants.<br>
 * The actual repository managing is delegated to <code>RepositorySet</code>s, while the logic
 * for binding these sets together is located in this class. Set actual <code>RepositorySet</code>s
 * to be used are defined in <code>login(...)</code>.<br>
 */
public class RepositoryAdminImpl implements RepositoryAdmin {
    /**
     * Maps from interface classes of the ObjectRepositories to their implementations.
     */
    @SuppressWarnings("unchecked")
    private Map<Class<? extends ObjectRepository>, ObjectRepositoryImpl> m_repositories;

    private final ChangeNotifier m_changeNotifier;
    private volatile BundleContext m_context; /* Injected by dependency manager */
    private volatile PreferencesService m_preferences; /* Injected by dependency manager */
    private volatile LogService m_log; /* Injected by dependency manager */

    private final Object m_lock = new Object();

    private final static String PREFS_LOCAL_FILE_ROOT = "ClientRepositoryAdmin";
    private final static String PREFS_LOCAL_FILE_LOCATION = "FileLocation";
    private final static String PREFS_LOCAL_FILE_CURRENT = "current";
    private final static String PREFS_LOCAL_FILE_BACKUP = "backup";
    private User m_user;
    private RepositorySet[] m_repositorySets;
    private final Activator m_repositoryFactory;

    RepositoryAdminImpl(Activator factory, ChangeNotifier changeNotifier) {
        m_repositoryFactory = factory;
        m_changeNotifier = changeNotifier;
    }

    @SuppressWarnings("unchecked")
    void initialize(Map<Class<? extends ObjectRepository>, ObjectRepositoryImpl> repositories) {
        m_repositories = repositories;
    }

    @SuppressWarnings("unchecked")
    public void start() {
        initialize(m_repositoryFactory.publishRepositories());
    }

    public void stop() {
        m_repositoryFactory.pullRepositories();
    }

    public void checkout() throws IOException {
        synchronized (m_lock) {
            ensureLogin();
            for (RepositorySet set : m_repositorySets) {
                set.checkout();
            }
            m_changeNotifier.notifyChanged(TOPIC_REFRESH_SUFFIX, null);
        }
    }

    public void commit() throws IOException {
        synchronized (m_lock) {
            ensureLogin();
            for (RepositorySet set : m_repositorySets) {
                set.commit();
            }
            m_changeNotifier.notifyChanged(TOPIC_REFRESH_SUFFIX, null);
        }
    }

    public void flush() throws IOException {
        synchronized (m_lock) {
            ensureLogin();
            for (RepositorySet set : m_repositorySets) {
                set.writeLocal();
                set.savePreferences();
            }
            m_changeNotifier.notifyChanged(TOPIC_FLUSHED_SUFFIX, null);
        }
    }

    public void revert() throws IOException {
        ensureLogin();
        synchronized (m_lock) {
            for (RepositorySet set : m_repositorySets) {
                set.revert();
            }
            m_changeNotifier.notifyChanged(TOPIC_REFRESH_SUFFIX, null);
        }
    }

    public boolean isCurrent() throws IOException {
        ensureLogin();
        synchronized (m_lock) {
            boolean result = true;
            for (RepositorySet set : m_repositorySets) {
                    result &= (set.isCurrent() || !set.writeAccess());
            }
            return result;
        }
    }

    public boolean isModified() {
        ensureLogin();
        boolean result = false;
        for (RepositorySet set : m_repositorySets) {
            result |= set.isModified();
        }
        return result;
    }

    public RepositoryAdminLoginContext createLoginContext(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User may not be null.");
        }
        return new RepositoryAdminLoginContextImpl(user);
    }

    public void login(RepositoryAdminLoginContext context) throws IOException {
        if (!(context instanceof RepositoryAdminLoginContextImpl)) {
            throw new IllegalArgumentException("Only the RepositoryAdminLoginContext returned by createLoginContext can be used.");
        }

        RepositoryAdminLoginContextImpl impl = ((RepositoryAdminLoginContextImpl) context);
        RepositorySet[] repositorySets = getRepositorySets(impl);
        // TODO I don't like this line, it should not be here...
        ((ArtifactRepositoryImpl) m_repositories.get(ArtifactRepository.class)).setObrBase(impl.getObrBase());
        login(impl.getUser(), repositorySets);
    }

    /**
     * Helper method for login; also allows injection of custom RepositorySet objects for
     * testing purposes.
     * @throws IOException
     */
    void login(User user, RepositorySet[] sets) throws IOException {
        synchronized(m_lock) {
            if (m_user != null) {
                throw new IllegalStateException("Another user is logged in.");
            }

            m_user = user;
            m_repositorySets = sets;
            for (RepositorySet set : m_repositorySets) {
                set.readLocal();
                set.loadPreferences();
            }
        }

        m_changeNotifier.notifyChanged(TOPIC_LOGIN_SUFFIX, null);
    }

    public void logout(boolean force) throws IOException {
        IOException exception = null;
        synchronized (m_lock) {
            ensureLogin();

            try {
                flush();
            }
            catch (IOException e) {
                if (!force) {
                    throw e;
                }
                else {
                    exception = e;
                }
            }

            for (RepositorySet set : m_repositorySets) {
                set.clearRepositories();
                set.unregisterHandler();
            }

            m_user = null;
            m_repositorySets = new RepositorySet[0];
        }
        m_changeNotifier.notifyChanged(TOPIC_LOGOUT_SUFFIX, null);
        if (exception != null) {
            throw exception;
        }
    }

    boolean loggedIn() {
        return m_user != null;
    }

    /**
     * Helper method to make sure a user is logged in.
     * @throws IllegalStateException
     */
    private void ensureLogin() throws IllegalStateException {
        if (!loggedIn()) {
            throw new IllegalStateException("This operation requires a user to be logged in.");
        }
    }

    /**
     * Helper method, creates RepositorySets based on the Login context.
     */
    @SuppressWarnings("unchecked")
    private RepositorySet[] getRepositorySets(RepositoryAdminLoginContextImpl context) throws IOException {
        // First, some sanity checks on the list of descriptors.
        for (RepositorySetDescriptor rsd : context.getDescriptors()) {
            for (Class c : rsd.m_objectRepositories) {
                // Do we have an impl for each repository class?
                if (!m_repositories.containsKey(c)) {
                    throw new IllegalArgumentException(rsd.toString() + " references repository class " + c.getName() + " for which no implementation is available.");
                }
                // Do other sets have a reference to this same class?
                for (RepositorySetDescriptor other : context.getDescriptors()) {
                    if (other != rsd) {
                        for (Class otherC : other.m_objectRepositories) {
                            if (c.equals(otherC)) {
                                throw new IllegalArgumentException(rsd.toString() + " references repository class " + c.getName() + ", but so does " + other.toString());
                            }
                        }
                    }
                }
            }
        }

        RepositorySet[] result = new RepositorySet[context.getDescriptors().size()];

        /*
         * Create the lists of repositories and topics, and create and register
         * the sets with these.
         */
        for (int nRsd = 0; nRsd < result.length; nRsd++) {
            RepositorySetDescriptor rsd = context.getDescriptors().get(nRsd);
            ObjectRepositoryImpl[] impls = new ObjectRepositoryImpl[rsd.m_objectRepositories.length];
            String[] topics = new String[rsd.m_objectRepositories.length];
            for (int nRepo = 0; nRepo < impls.length; nRepo++) {
                impls[nRepo] = m_repositories.get(rsd.m_objectRepositories[nRepo]);
                topics[nRepo] = impls[nRepo].getTopicAll(true);
            }
            result[nRsd] = loadRepositorySet(context.getUser(), rsd, impls);
            result[nRsd].registerHandler(m_context, topics);
        }

        return result;
    }

    /**
     * Helper method for login.
     */
    private Preferences getRepositoryPrefs(Preferences userPrefs, URL location, String customer, String name) {
        // Note: we can only use the getAuthority part of the URL for indexing, because the full URL will contain
        // in the protocol part.
        Preferences repoPref = userPrefs.node(location.getAuthority() + location.getPath());
        Preferences customerPref = repoPref.node(customer);
        return customerPref.node(name);
    }

    /**
     * Helper method for login.
     * @throws IOException
     */
    private File getFileFromPreferences(Preferences repositoryPrefs, String type) throws IOException {
        String directory = repositoryPrefs.get(PREFS_LOCAL_FILE_LOCATION, "");

        if ((directory == "") || !m_context.getDataFile(PREFS_LOCAL_FILE_ROOT + "/" + directory).isDirectory()) {
            if (!m_context.getDataFile(PREFS_LOCAL_FILE_ROOT + "/" + directory).isDirectory() && (directory != "")) {
                m_log.log(LogService.LOG_WARNING, "Directory '" + directory + "' should exist according to the preferences, but it does not.");
            }
            // The file did not exist, so create a new one.
            File directoryFile = null;
            try {
                File bundleDataDir = m_context.getDataFile(PREFS_LOCAL_FILE_ROOT);
                if (!bundleDataDir.isDirectory()) {
                    if (!bundleDataDir.mkdir()) {
                        throw new IOException("Error creating the local repository root directory.");
                    }
                }
                directoryFile = File.createTempFile("repo", "", bundleDataDir);
            }
            catch (IOException e) {
                // We cannot create the temp file? Then something is seriously wrong, so rethrow.
                throw e;
            }

            directoryFile.delete(); // No problem if this goes wrong, it just means it wasn't there yet.
            if (!directoryFile.mkdir()) {
                throw new IOException("Error creating the local repository storage directory.");
            }
            repositoryPrefs.put(PREFS_LOCAL_FILE_LOCATION, directoryFile.getName());
            return new File(directoryFile, type);
        }
        else {
            // Get the given file from that location.
            return m_context.getDataFile(PREFS_LOCAL_FILE_ROOT + "/" + directory + "/" + type);
        }
    }

    /**
     * Helper method for login.
     * @throws IOException
     */
    private BackupRepository getBackupFromPreferences(Preferences repositoryPrefs) throws IOException {
        File current = getFileFromPreferences(repositoryPrefs, PREFS_LOCAL_FILE_CURRENT);
        File backup = getFileFromPreferences(repositoryPrefs, PREFS_LOCAL_FILE_BACKUP);
        return new FilebasedBackupRepository(current, backup);
    }

    /**
     * Helper method for login.
     * @throws IOException
     */
    private CachedRepository getCachedRepositoryFromPreferences(User user, Repository repository, Preferences repositoryPrefs) throws IOException {
        long mostRecentVersion = repositoryPrefs.getLong("version", CachedRepositoryImpl.UNCOMMITTED_VERSION);
        return new CachedRepositoryImpl(user, repository, getBackupFromPreferences(repositoryPrefs), mostRecentVersion);
    }

    /**
     * Helper method for login, which loads a set of repositories.
     * @param user A <code>User</code> object
     * @param rsd A RepositorySetDescriptor, defining the set to be created.
     * @param repos An array of <code>ObjectRepositoryImpl</code> which this set should manage. Each
     * @return The newly created repository set.
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public RepositorySet loadRepositorySet(User user, RepositorySetDescriptor rsd, ObjectRepositoryImpl[] repos) throws IOException {
        Repository repo = new RemoteRepository(rsd.m_location, rsd.m_customer, rsd.m_name);
        Preferences prefs = m_preferences.getUserPreferences(user.getName());
        Preferences repoPrefs = getRepositoryPrefs(prefs, rsd.m_location, rsd.m_customer, rsd.m_name);
        return new RepositorySet(m_changeNotifier, m_log, user, repoPrefs, repos, getCachedRepositoryFromPreferences(user, repo, repoPrefs), rsd.m_name, rsd.m_writeAccess);
    }

    public int getNumberWithWorkingState(Class<? extends RepositoryObject> clazz, WorkingState state) {
        int result = 0;
        for (RepositorySet set : m_repositorySets) {
            result += set.getNumberWithWorkingState(clazz, state);
        }
        return result;
    }

    public WorkingState getWorkingState(RepositoryObject object) {
        for (RepositorySet set : m_repositorySets) {
            WorkingState result = set.getWorkingState(object);
            if (result != null) {
                return result;
            }
        }
        return WorkingState.Unchanged;
    }
}
