package net.luminis.liq.log;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

public interface AlertEvent {
	
    public static final String KEY_TYPE = "type";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_USER = "user";
    public static final String KEY_MSG = "message";
    public static final DateFormat DATE_FORMAT = new SimpleDateFormat("d/m/yyyy h:mm:ss a");

    public static final int BASE = 0;

    /**
     * When SuperQ produces logging info, this event is logged with the following properties:
     * <dl>
     * <dt>id</dt>
     * <dd>Error Number</dd>
     * <dt>date</dt>
     * <dd>Date the event occurred, in format: <pre>d/m/yyyy h:mm:ss a</pre></dd>
     * <dt>user</dt>
     * <dd>The related user</dd>
     * <dt>message</dt>
     * <dd>Textual description of the event</dd>
     * </dl>
     */
    public static final int SUPERQ = (BASE + 1);
    
    /**
     * When SuperQ produces logging info in a format that is not valid or recognized, this event
     * is logged with the following properties:
     * <dl>
     * <dt>message</dt>
     * <dd>The raw message that could not be recognized</dd>
     * </dl>
     */
    public static final int SUPERQ_UNKNOWNFORMAT = (BASE + 2);
}
