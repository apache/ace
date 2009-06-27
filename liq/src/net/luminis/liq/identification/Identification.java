package net.luminis.liq.identification;


/**
 * The <code>Identification</code> can be used to identify the system it is running on. This
 * identification is both unique and should not change. Nevertheless, don't cache the value
 * but simply ask this service when you need it.
 */
public interface Identification {

    /**
     * Returns the identification of the system.
     *
     * @return the identification, or <code>null</code> if the identification could not be obtained
     */
    public String getID();

}
