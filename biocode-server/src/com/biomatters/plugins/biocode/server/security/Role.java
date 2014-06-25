package com.biomatters.plugins.biocode.server.security;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Matthew Cheung
 *         Created on 13/06/14 1:51 PM
 */
@XmlRootElement
public class Role {
    public static final Role ADMIN = new Role(0, "ADMIN", "The admin of this project");
    public static final Role WRITER = new Role(1, "WRITER", "Can modify data");
    public static final Role READER = new Role(2, "READER", "Can only read");

    public int id;
    public String name;
    public String description = "";

    public Role() {
    }

    public Role(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public boolean isAtLeast(Role role) {
        return id <= role.id;
    }
}
