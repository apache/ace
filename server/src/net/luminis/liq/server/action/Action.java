package net.luminis.liq.server.action;

import org.osgi.service.event.Event;

/**
 * An Action is a rather general action which can be used to handle some event.
 * Action services are to be published with a service property {@link Action#ACTION_NAME},
 * which states the name of this action, so other services can find it.<br>
 * <br>
 * Implementers of Action should specify in their interface which properties should
 * be available in the event, for the action to be able to do its job.
 */
public interface Action {
    public static final String ACTION_NAME = Action.class.getName() + ".name";
    
    /**
     * Handles an event, performing the main action of this Action.
     */
    public void handle(Event event);

}
