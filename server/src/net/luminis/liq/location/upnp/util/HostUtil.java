package net.luminis.liq.location.upnp.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class HostUtil {

	private HostUtil(){}
	
	
	public static final String getHost() {
		String host;
		
        InetAddress inet;
		try {
			inet = InetAddress.getLocalHost();
	        host = inet.getHostAddress();
		} catch (UnknownHostException e) {
			System.out.println("Warning: enable to create host name");
			host = "localhost";
		}
		
		return host;
	}
	
	
	
}
