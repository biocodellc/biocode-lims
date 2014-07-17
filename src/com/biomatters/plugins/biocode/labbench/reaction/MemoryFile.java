package com.biomatters.plugins.biocode.labbench.reaction;

import javax.xml.bind.annotation.XmlRootElement;

/**
* @author Matthew Cheung
* @version $Id$
*          <p/>
*          Created on 7/04/14 11:43 AM
*/
@XmlRootElement
public class MemoryFile {
    private int databaseId;
    private String name;
    private byte[] data;

    public MemoryFile() {
    }

    public MemoryFile(String name, byte[] data) {
        this.name = name;
        this.data = data;
        this.databaseId = -1;
    }

    public MemoryFile(int databaseId, String name, byte[] data) {
        this.databaseId = databaseId;
        this.name = name;
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public byte[] getData() {
        return data;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }
}
