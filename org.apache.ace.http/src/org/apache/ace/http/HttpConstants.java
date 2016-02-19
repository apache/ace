package org.apache.ace.http;

import static org.osgi.service.http.whiteboard.HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME;

public class HttpConstants {
    
    public static final String ACE_WHITEBOARD_CONTEXT_NAME = "org.apache.ace";
    
    public static final String ACE_WHITEBOARD_CONTEXT_SELECT_FILTER = "(" + HTTP_WHITEBOARD_CONTEXT_NAME + "=" + ACE_WHITEBOARD_CONTEXT_NAME + ")";
    
}
