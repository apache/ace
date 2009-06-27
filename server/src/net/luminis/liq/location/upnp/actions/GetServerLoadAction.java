package net.luminis.liq.location.upnp.actions;

import java.util.Dictionary;
import java.util.Hashtable;

import net.luminis.liq.location.LocationService;

import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPStateVariable;


public class GetServerLoadAction implements UPnPAction {

	final private String NAME = "GetServerLoad";
	final private String RET_TARGET_VALUE = "RetServerLoadValue";
	final private String[] OUT_ARG_NAMES = new String[]{RET_TARGET_VALUE};
	private UPnPStateVariable state;

	private final LocationService m_locationService;


	public GetServerLoadAction(LocationService ls) {
		m_locationService = ls;
		state = new StateVarImpl();
	}

	public String getName() {
		return NAME;
	}

	public String getReturnArgumentName() {
		return RET_TARGET_VALUE;
	}

	public String[] getInputArgumentNames() {

		return null;
	}

	public String[] getOutputArgumentNames() {
		return OUT_ARG_NAMES;
	}

	public UPnPStateVariable getStateVariable(String argumentName) {
		return state;
	}

	public Dictionary invoke(Dictionary args) throws Exception {
		int load = m_locationService.getServerLoad();

		Hashtable result = new Hashtable();
		result.put(RET_TARGET_VALUE, load);
		return result;
	}

	private class StateVarImpl extends StateVar {


		public String getName() {
			return "ServerLoad";
		}

		public Object getCurrentValue() {
			return m_locationService.getServerLoad();
		}

		public Class getJavaDataType() {
			return Integer.TYPE;
		}

		public String getUPnPDataType() {
			return TYPE_INT;
		}

        @Override
        public Number getMinimum() {
		    return 0;
		}

        @Override
        public Number getMaximum() {
            return 100;
        }

        @Override
        public Number getStep() {
            return 1;
        }
	}

}
