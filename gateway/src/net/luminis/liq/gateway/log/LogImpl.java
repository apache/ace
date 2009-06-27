package net.luminis.liq.gateway.log;

import java.io.IOException;
import java.util.Dictionary;

import net.luminis.liq.gateway.log.store.LogStore;
import net.luminis.liq.log.LogEvent;
import net.luminis.liq.log.Log;

import org.osgi.service.log.LogService;

public class LogImpl implements Log {
    private volatile LogStore m_store;
    private volatile LogService m_log;

    public void log(int type, Dictionary properties) {
        try {
            m_store.put(type, properties);
        }
        catch (NullPointerException e) {
            // if we cannot store the event, we log it to the normal log as extensively as possible
            m_log.log(LogService.LOG_WARNING, "Could not store event: " + (new LogEvent("", 0, 0, 0, type, properties)).toRepresentation(), e);
        }
        catch (IOException e) {
            // if we cannot store the event, we log it to the normal log as extensively as possible
            m_log.log(LogService.LOG_WARNING, "Could not store event: " + (new LogEvent("", 0, 0, 0, type, properties)).toRepresentation(), e);
        }
    }
}
