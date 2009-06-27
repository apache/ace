package net.luminis.liq.log.listener;

import java.util.Dictionary;

import net.luminis.liq.log.Log;

/**
 * Class responsible for being the object to talk to when trying to log events. This class
 * will decide whether to log it to cache, or to the actual log.
 */
public class LogProxy implements Log {

    private Log m_log;
    private LogCache m_logCache;

    public LogProxy() {
        m_logCache = new LogCache();
    }

    /**
     * Logs the log entry either to the real log service or to the cache, depending on
     * whether the real service is available.
     */
    public synchronized void log(int type, Dictionary properties) {
        if (m_log != null) {
            m_log.log(type, properties);
        }
        else {
            m_logCache.log(type, properties);
        }
    }

    /**
     * Sets the log, and flushes the cached log entries when a log service
     * is passed into this method. When null is passed as parameter, the log service
     * is not available anymore, and the cache should be used instead.
     * @param log  the log service to use, when null the cache will be used instead
     */
    public synchronized void setLog(Log log) {
        m_log = log;
        // flush content of the cache to the real log
        m_logCache.flushTo(m_log);
    }
}
