package net.luminis.liq.location;

import java.net.URL;

public interface LocationService {
	public URL getLocation();
	public String getServerType();
	public int getServerLoad();
}
