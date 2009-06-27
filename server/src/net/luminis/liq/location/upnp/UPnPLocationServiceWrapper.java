package net.luminis.liq.location.upnp;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import net.luminis.liq.location.LocationService;
import net.luminis.liq.location.upnp.actions.GetLocationAction;
import net.luminis.liq.location.upnp.actions.GetServerLoadAction;
import net.luminis.liq.location.upnp.actions.GetServerTypeAction;

import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPService;
import org.osgi.service.upnp.UPnPStateVariable;

public class UPnPLocationServiceWrapper implements UPnPService, LocationService {

	final static private String SERVICE_ID = "urn:upnp-org:serviceId:LocationService:1";

	//managed
	private volatile LocationService m_ls;

	volatile private Map<String, UPnPAction> m_actions;


	public UPnPLocationServiceWrapper() {

		m_actions = new HashMap<String, UPnPAction>();

		UPnPAction location = new GetLocationAction(this);
		m_actions.put(location.getName(), location);

		UPnPAction type = new GetServerTypeAction(this);
		m_actions.put(type.getName(), type);

		UPnPAction load = new GetServerLoadAction(this);
		m_actions.put(load.getName(), load);
	}

	public URL getLocation() {
		return m_ls.getLocation();
	}

	public String getServerType() {
		return m_ls.getServerType();
	}

	public int getServerLoad() {
		return (int)(40 + Math.random()*10);
	}


	public UPnPAction getAction(String actionName) {
		return m_actions.get(actionName);
	}

	public UPnPAction[] getActions() {
		return m_actions.values().toArray(new UPnPAction[0]);
	}

	public String getId() {
		return SERVICE_ID;
	}

	public UPnPStateVariable getStateVariable(String id) {
		UPnPAction action = m_actions.get(id);
		if (action != null) {
			return action.getStateVariable(null);
		}
		return null;
	}

	public UPnPStateVariable[] getStateVariables() {
		int i=0;
		UPnPStateVariable[] states = new UPnPStateVariable[m_actions.size()];
		for (UPnPAction action : m_actions.values()) {
			states[i++] = action.getStateVariable(null);
		}
		return states;
	}

	public String getType() {
		return UPnPConstants.LOCATION_SERVICE_TYPE;
	}


	public String getVersion() {
		return "1";
	}



}