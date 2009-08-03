package com.biomatters.plugins.moorea.labbench;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 7/07/2009 11:02:05 AM
 */
public class BadDataException extends Exception{
    public BadDataException() {
    }

    public BadDataException(String message) {
        super(message);
    }

    public BadDataException(String message, Throwable cause) {
        super(message, cause);
    }

    public BadDataException(Throwable cause) {
        super(cause);
    }
}
