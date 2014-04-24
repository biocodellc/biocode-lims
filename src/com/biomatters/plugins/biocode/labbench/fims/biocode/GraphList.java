package com.biomatters.plugins.biocode.labbench.fims.biocode;

import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 3/02/14 5:30 PM
 */


@XmlRootElement
public class GraphList {
    List<Graph> data;

    public GraphList() {
    }

    public List<Graph> getData() {
        return data;
    }

    public void setData(List<Graph> data) {
        this.data = data;
    }
}
