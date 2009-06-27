package net.luminis.liq.log;

import static net.luminis.liq.test.utils.TestUtils.UNIT;

import java.util.Dictionary;
import java.util.Properties;

import org.testng.annotations.Test;

public class LogEventTest {
    @Test(groups = { UNIT })
    public void serializeLogEvent() {
        LogEvent e = new LogEvent("gwid", 1, 2, 3, AuditEvent.FRAMEWORK_STARTED, new Properties());
        assert e.toRepresentation().equals("gwid,1,2,3," + AuditEvent.FRAMEWORK_STARTED);
        Properties p = new Properties();
        p.put(AuditEvent.KEY_ID, "my first value");
        e = new LogEvent("gwid", 1, 2, 3, AuditEvent.BUNDLE_INSTALLED, p);
        assert e.toRepresentation().equals("gwid,1,2,3," + AuditEvent.BUNDLE_INSTALLED + "," + AuditEvent.KEY_ID + ",my first value");
        e = new LogEvent("gwid,gwid\n\r$", 1, 2, 3, AuditEvent.FRAMEWORK_STARTED, new Properties());
        assert e.toRepresentation().equals("gwid$kgwid$n$r$$,1,2,3," + AuditEvent.FRAMEWORK_STARTED);
    }

    @SuppressWarnings("unchecked")
    @Test(groups = { UNIT })
    public void deserializeLogEvent() {
        LogEvent e = new LogEvent("gwid$kgwid$n$r$$,1,2,3," + AuditEvent.FRAMEWORK_STARTED + ",a,1,b,2,c,3");
        assert e.getGatewayID().equals("gwid,gwid\n\r$") : "Gateway ID is not correctly parsed";
        assert e.getLogID() == 1 : "Log ID is not correctly parsed";
        assert e.getID() == 2 : "ID is not correctly parsed";
        assert e.getTime() == 3 : "Time is not correctly parsed";
        assert e.getType() == AuditEvent.FRAMEWORK_STARTED : "Event type is wrong";
        Dictionary p = e.getProperties();
        assert p != null : "Properties are not correctly parsed";
        assert p.get("a").equals("1") : "Property a should be 1";
        assert p.get("b").equals("2") : "Property a should be 1";
        assert p.get("c").equals("3") : "Property a should be 1";
    }
    @Test(groups = { UNIT })
    public void deserializeIllegalLogEvent() {
        try {
            new LogEvent("garbage in, garbage out!");
            assert false : "Parsing garbage should result in an exception";
        }
        catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new LogEvent("g$z,1,2,3," + AuditEvent.BUNDLE_STOPPED);
            assert false : "Parsing illegal token should result in an exception";
        }
        catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new LogEvent("g$,1,2,3," + AuditEvent.BUNDLE_STOPPED);
            assert false : "Parsing half of a token should result in an exception";
        }
        catch (IllegalArgumentException e) {
            // expected
        }
        try {
            new LogEvent("g$,1,2,3," + AuditEvent.BUNDLE_STOPPED + ",a");
            assert false : "Parsing only a key should result in an exception";
        }
        catch (IllegalArgumentException e) {
            // expected
        }
    }
}
