package net.luminis.liq.log.listener;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;

import net.luminis.liq.log.Log;

/**
 * This cache is used whenever the real log service is not available. When
 * the real log becomes available, all cached log entries should be flushed
 * to the real log service and leaving the cache empty afterwards.
 */
public class LogCache implements Log {

    private final List m_logEntries = new ArrayList();

    /**
     * Log the entry in the cache for flushing to the real log service later on.
     */
    public synchronized void log(int type, Dictionary properties) {
        m_logEntries.add(new LogEntry(type, properties));
    }

    /**
     * Flushes all cached log entries to the specified Log and leaves the cache empty
     * after flushing. Will do nothing when a null is passed as parameter.
     * @param log  The log service to flush the cached log entries to
     */
    public synchronized void flushTo(Log log) {
        if (log != null) {
            for (Iterator iterator = m_logEntries.iterator(); iterator.hasNext();) {
                LogEntry entry = (LogEntry) iterator.next();
                log.log(entry.getType(), entry.getProperties());
            }
            m_logEntries.clear();
        }
        else {
            // do nothing, as you want to keep using the cache
        }
    }

    private class LogEntry {
        private int m_type;
        private Dictionary m_properties;
        public LogEntry(int type, Dictionary properties) {
            m_type = type;
            m_properties = properties;
        }

        public int getType() {
            return m_type;
        }

        public Dictionary getProperties() {
            return m_properties;
        }
    }
}
