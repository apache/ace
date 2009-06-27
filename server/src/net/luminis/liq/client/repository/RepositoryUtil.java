package net.luminis.liq.client.repository;

public class RepositoryUtil {
	/**
	 * Before passing user input into an LDAP filter, some precautions need to be taken
	 * (see section 3.2.6 of the OSGi core specification). This function escapes
	 * illegal characters, and returns the resulting string.
	 */
    public static String escapeFilterValue(String value) {
        return value.replaceAll("\\\\", "\\\\\\\\")
                    .replaceAll("\\(", "\\\\(")
                    .replaceAll("\\)", "\\\\)")
                    .replaceAll("\\*", "\\\\*");
    }
}
