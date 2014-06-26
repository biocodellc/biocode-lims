package com.biomatters.plugins.biocode.server.security;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Matthew Cheung
 *         Created on 26/06/14 1:42 PM
 */
@XmlRootElement
public class UserRole {
    public User user;
    public Role role;

    public UserRole() {
    }

    public static List<UserRole> forMap(Map<User, Role> map) {
        List<UserRole> list = new ArrayList<UserRole>();
        for (Map.Entry<User, Role> entry : map.entrySet()) {
            UserRole userRole = new UserRole();
            userRole.user = entry.getKey();
            userRole.role = entry.getValue();
            list.add(userRole);
        }
        return list;
    }
}
