package com.biomatters.plugins.biocode.server.security;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.Arrays;
import java.util.List;

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

    private Role(int id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    static Role forId(int id) {
        for (Role role  : values()) {
            if(role.id == id) {
                return role;
            }
        }
        return null;
    }

    static List<Role> values() {
        return Arrays.asList(ADMIN, WRITER, READER);
    }

    public boolean isAtLeast(Role role) {
        return id <= role.id;
    }
}
