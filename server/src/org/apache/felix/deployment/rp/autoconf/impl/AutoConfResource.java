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
package org.apache.felix.deployment.rp.autoconf.impl;

import java.util.Dictionary;

public class AutoConfResource {

	public String m_pid;
	public String m_factoryPid;
	public Dictionary m_oldProps;
	public Dictionary m_newProps;

	public AutoConfResource(String pid, String factoryPid, Dictionary oldProps, Dictionary newProps) {
		m_pid = pid;
		m_factoryPid = factoryPid;
		m_newProps = oldProps;
		m_newProps = newProps;
	}

	public String getPid() {
		return m_pid;
	}

	public String getFactoryPid() {
		return m_factoryPid;
	}

	public Dictionary getOldProps() {
		return m_oldProps;
	}

	public Dictionary getNewProps() {
		return m_newProps;
	}
}
