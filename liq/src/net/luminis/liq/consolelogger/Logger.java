package net.luminis.liq.consolelogger;

import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

public class Logger implements LogService {
    private static String[] LEVEL = { "", "Error", "Warn ", "Info ", "Debug" };

    public void log(int level, String message) {
        log(null, level, message, null);
    }

    public void log(int level, String message, Throwable throwable) {
        log(null, level, message, throwable);
    }

    public void log(ServiceReference reference, int level, String message) {
        log(reference, level, message, null);
    }

    public void log(ServiceReference reference, int level, String message, Throwable throwable) {
        String bundle = " [   ]";
        String service = " ";
        if (reference != null) {
            bundle = "00" + reference.getBundle().getBundleId();
            bundle = " [" + bundle.substring(bundle.length() - 3) + "]";
            Object objectClass = reference.getProperty(Constants.OBJECTCLASS);
            if (objectClass instanceof String[]) {
                StringBuffer buffer = new StringBuffer();
                String[] objClassArr = ((String[]) objectClass);
                for (int i = 0; i < objClassArr.length; i++) {
                    String svc = objClassArr[i];
                    if (buffer.length() > 0) {
                        buffer.append(';');
                    }
                    buffer.append(svc);
                    service = buffer.toString() + ": ";
                }
            }
            else {
                service = objectClass.toString() + ": ";
            }
        }
        System.out.println("[" + LEVEL[level] + "]" + bundle + service + message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
}
