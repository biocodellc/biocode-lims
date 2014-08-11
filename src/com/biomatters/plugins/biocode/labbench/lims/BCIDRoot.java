package com.biomatters.plugins.biocode.labbench.lims;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Gen Li
 *         Created on 5/08/14 4:26 PM
 */
@XmlRootElement
public class BCIDRoot {
    public String type;
    public String value;

    public BCIDRoot(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public BCIDRoot() {

    }
}