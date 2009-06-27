package net.luminis.liq.log.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Dictionary;
import java.util.List;

import net.luminis.liq.log.Log;

public class MockLog implements Log {

    @SuppressWarnings("unchecked")
    private List m_logEntries;

    @SuppressWarnings("unchecked")
    public MockLog() {
        m_logEntries = Collections.synchronizedList(new ArrayList());
    }

    @SuppressWarnings("unchecked")
    public void log(int type, Dictionary properties) {
        m_logEntries.add(new LogEntry(type, properties));
    }

    @SuppressWarnings("unchecked")
    public List getLogEntries() {
        return new ArrayList(m_logEntries);
    }

    public void clear() {
        m_logEntries.clear();
    }

    public class LogEntry {
        private int m_type;
        @SuppressWarnings("unchecked")
        private Dictionary m_properties;
        @SuppressWarnings("unchecked")
        public LogEntry(int type, Dictionary properties) {
            m_type = type;
            m_properties = properties;
        }

        public int getType() {
            return m_type;
        }

        @SuppressWarnings("unchecked")
        public Dictionary getProperties() {
            return m_properties;
        }
    }
}
