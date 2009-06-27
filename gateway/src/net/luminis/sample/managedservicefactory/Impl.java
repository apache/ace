package net.luminis.sample.managedservicefactory;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;

public class Impl implements ManagedServiceFactory {

    private List m_pids = new ArrayList();

    public synchronized void deleted(String pid) {
        System.out.println("Factory instance removed: " + pid);
        m_pids.remove(pid);
        System.out.println("Remaining instances: " + m_pids);
    }

    public String getName() {
        return "Sample Managed Service Factory";
    }

    public synchronized void updated(String pid, Dictionary dictionary) throws ConfigurationException {
        System.out.println("New factory instance: " + pid);
        System.out.println("Other instances:" + m_pids);
        m_pids.add(pid);
        if(dictionary != null) {
            Enumeration keys = dictionary.keys();
            System.out.println("Dictionary contains:");
            while (keys.hasMoreElements()) {
                Object nextElement = keys.nextElement();
                System.out.println("KEY=(" + nextElement + ") VALUE=(" + dictionary.get(nextElement) +")");
            }
        } else {
            System.out.println("Empty dictionary was supplied.");
        }
    }

}
