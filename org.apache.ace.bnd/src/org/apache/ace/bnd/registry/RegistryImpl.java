package org.apache.ace.bnd.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import aQute.bnd.service.Registry;

/**
 * BND Registry implementation that can be used when using bnd plugins 
 * outside a bnd workspace. 
 *
 */
public class RegistryImpl implements Registry {

    private final List<Object> m_plugins;
    
    public RegistryImpl() {
        m_plugins = new ArrayList<>();
    }
    
    public RegistryImpl(Object... plugins) {
        m_plugins = new ArrayList<>(Arrays.asList(plugins));
    }
    
    public void addPlugin(Object plugin) {
        m_plugins.add(plugin);
    }
    
    public void removePlugin(Object plugin) {
        m_plugins.remove(plugin);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> getPlugins(Class<T> c) {
        
        List<T> plugins = new ArrayList<>();
        for (Object plugin: m_plugins) {
            if (c.isInstance(plugin)){
                plugins.add((T)plugin);
            }            
        }
        return plugins;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getPlugin(Class<T> c) {
        for (Object plugin: m_plugins) {
            if (c.isInstance(plugin)){
                return (T)plugin;
            }            
        }
        return null;
    }

}
