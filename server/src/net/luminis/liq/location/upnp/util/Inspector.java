package net.luminis.liq.location.upnp.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;
import java.util.Properties;

import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPService;
import org.osgi.service.upnp.UPnPStateVariable;


public class Inspector {

	
	
	private static UPnPService createService(Class iface, Object target) throws Exception {
		Method[] methods = iface.getMethods();
		
		List<UPnPAction> list = new ArrayList<UPnPAction>();
		for (Method method : methods) {
			list.add(createAction(method, target));
		}
		return null;
	}
	
	
	
	private static UPnPAction createAction(Method m, Object target) {
		return new UPnPActionImpl(m, target);
	}
	
	
	
	
	private static class UPnPActionImpl implements UPnPAction {
		
		private final Method m_method;
		private final Object m_target;
		
		public UPnPActionImpl(Method m, Object t) {
			m_method = m;
			m_target = t;
		}
		
		
		public String getName() {
			return m_method.getName();
		}
		
		public String[] getInputArgumentNames() {
			Class[] inputArgTypes = m_method.getParameterTypes();
			String[] inputArgs = new String[inputArgTypes.length];
			
			if (inputArgs.length == 1) {
				inputArgs[0] = inputArgTypes[0].getSimpleName();
			}
			else {
				int i = 0;
				for (Class inputType : inputArgTypes) {
					inputArgs[i] = inputArgTypes[i].getSimpleName() + i;
					i++;
				}
			}
			return inputArgs;
		}
		
		public String[] getOutputArgumentNames() {
			return new String[]{getReturnArgumentName()};
		}
		
		public String getReturnArgumentName() {
			Class returnType = m_method.getReturnType();
			return returnType.getSimpleName();
		}
		
		public UPnPStateVariable getStateVariable(String arg0) {
			// TODO Auto-generated method stub
			return null;
		}
		
		public Dictionary invoke(Dictionary dict) throws Exception {
			String[] argNames = getInputArgumentNames();
			Object[] args = new Object[argNames.length];
			
			int i=0;
			for (String name : argNames) {
				args[i++] = dict.get(name);
			}
			
			Object retVal = m_method.invoke(m_target, args);
			if (retVal==null) {
				return null;
			}
			
			Properties p = new Properties();
			p.put(getReturnArgumentName(), retVal);
			return p;
		}
		
		
		
	}
}
