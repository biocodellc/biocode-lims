package com.biomatters.plugins.moorea;

/**
 * @author Steven Stones-Havas
 * @version $Id$
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
