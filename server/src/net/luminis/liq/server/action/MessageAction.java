package net.luminis.liq.server.action;

/**
 * MessageAction is an action that sends out messages. To do so, it needs a user, and
 * both a short and a long description. Is it up to the implementation to do something with
 * it, e.g. use the short description and a subject for an email message, or use the short
 * description as the text in an SMS.
 */
public interface MessageAction extends Action {
    /**
     * Key for the event properties containing a User.
     */
    public static final String USER = "user";
    /**
     * Key for the event properties containing a description as a String.
     */
    public static final String DESCRIPTION = "description";
    /**
     * Key for the event properties containing a very short description as a String.
     */
    public static final String SHORT_DESCRIPTION = "shortDescription";
}
