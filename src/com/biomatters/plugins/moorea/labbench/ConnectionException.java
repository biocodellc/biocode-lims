package com.biomatters.plugins.moorea.labbench;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 13/05/2009
 * Time: 5:31:06 AM
 * To change this template use File | Settings | File Templates.
 */
public class ConnectionException extends Exception{
    public ConnectionException() {
        super();
    }

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionException(Throwable cause) {
        super(cause);
    }
}
