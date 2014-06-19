package com.biomatters.plugins.biocode.server.security;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Matthew Cheung
 *         Created on 13/06/14 1:51 PM
 */
@XmlRootElement
public enum Role {

    ADMIN(0),WRITER(1),READER(2);

    private int id;

    Role(int id) {
        this.id = id;
    }

    public boolean isAtLeast(Role role) {
        return id <= role.id;
    }
}
