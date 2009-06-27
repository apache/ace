package net.luminis.liq.location.upnp.actions;

import org.osgi.service.upnp.UPnPLocalStateVariable;

public abstract class StateVar implements UPnPLocalStateVariable {

	public String[] getAllowedValues() {
		return null;
	}

	public Object getDefaultValue() {
		return null;
	}

	public Number getMinimum() {
		return null;
	}

	public Number getMaximum() {
		return null;
	}

	public Number getStep() {
		return null;
	}

	public boolean sendsEvents() {
		return false;
	}
}