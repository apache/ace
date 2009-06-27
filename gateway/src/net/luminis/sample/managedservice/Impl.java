package net.luminis.sample.managedservice;

import java.util.Dictionary;
import java.util.Enumeration;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;

public class Impl implements ManagedService {

    public synchronized void updated(Dictionary dictionary) throws ConfigurationException {
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
