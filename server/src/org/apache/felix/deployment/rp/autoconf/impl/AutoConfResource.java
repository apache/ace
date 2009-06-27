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
