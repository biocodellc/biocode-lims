package com.biomatters.plugins.biocode.server;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Matthew Cheung
 *         Created on 9/06/14 1:32 PM
 */
@XmlRootElement
public class User {
    public String username;
    public String password;

    public User() {
    }
}
