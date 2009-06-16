package com.biomatters.plugins.moorea;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 16/06/2009 3:12:41 PM
 */
public class Workflow {
    private int id;
    private String name;

    public Workflow(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
