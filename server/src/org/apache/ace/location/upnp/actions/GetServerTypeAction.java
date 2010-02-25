/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ace.location.upnp.actions;

import java.util.Dictionary;
import java.util.Hashtable;

import org.apache.ace.location.LocationService;
import org.osgi.service.upnp.UPnPAction;
import org.osgi.service.upnp.UPnPStateVariable;

public class GetServerTypeAction implements UPnPAction {

	final private String NAME = "GetServerType";
	final private String RET_TARGET_VALUE = "RetServerTypeValue";
	final private String[] OUT_ARG_NAMES = new String[]{RET_TARGET_VALUE};
	private UPnPStateVariable state;

	private final LocationService m_locationService;


	public GetServerTypeAction(LocationService ls) {
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
		String type = m_locationService.getServerType();

		Hashtable result = new Hashtable();
		result.put(RET_TARGET_VALUE, type);
		return result;
	}

	private class StateVarImpl extends StateVar {
		public String getName() {
			return "ServerType";
		}

		public Object getCurrentValue() {
			return m_locationService.getServerType();
		}

		public Class getJavaDataType() {
			return String.class;
		}

		public String getUPnPDataType() {
			return TYPE_STRING;
		}
	}
}
