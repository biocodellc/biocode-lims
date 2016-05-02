package com.biomatters.plugins.biocode.labbench;

/**
 * @author Steven Stones-Havas
 *          <p/>
 *          Created on 10/06/2009 9:17:24 PM
 */
public class TransactionException extends Exception{

    public TransactionException() {
        super();
    }

    public TransactionException(String message) {
        super(message);
    }

    public TransactionException(String message, Throwable cause) {
        super(message+(cause.getMessage() != null ? ", "+cause.getMessage() : ""), cause);
    }
}
