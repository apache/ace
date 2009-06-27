package net.luminis.liq.client.repository.helper.configuration;

import net.luminis.liq.client.repository.helper.ArtifactHelper;

/**
 * Definitions for ConfigurationHelper,used to treat an artifact as an AutoConf file.
 */
public interface ConfigurationHelper extends ArtifactHelper {
    public static final String KEY_FILENAME = "filename";

    public static final String MIMETYPE = "application/xml:osgi-autoconf";
    public static final String PROCESSOR = "org.osgi.deployment.rp.autoconf";
}
