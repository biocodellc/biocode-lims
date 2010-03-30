package com.biomatters.plugins.biocode.labbench;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 13/05/2009
 * Time: 5:31:06 AM
 * To change this template use File | Settings | File Templates.
 */
public class ConnectionException extends Exception{
    private String mainMessage;

    public ConnectionException() {
        super();
    }

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, String mainMessage) {
        super(message);
        this.mainMessage = mainMessage;
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionException(Throwable cause) {
        super(cause);
    }

    public String getMainMessage() {
        return mainMessage;
    }
}
