package com.biomatters.plugins.biocode.labbench.fims.biocode;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Matthew Cheung
 *         Created on 3/02/14 4:56 PM
 */
@XmlRootElement
public class Graph {
    @XmlElement(name = "project_code") public String projectCode;
    @XmlElement(name = "project_title") public String projectTitle;
    @XmlElement(name = "ts") public String ts;
    @XmlElement(name = "graph") public String graphId;

    public Graph() {
    }

    public Graph(String projectCode, String projectTitle, String ts, String graphId) {
        this.projectCode = projectCode;
        this.projectTitle = projectTitle;
        this.ts = ts;
        this.graphId = graphId;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getProjectTitle() {
        return projectTitle;
    }

    public String getTs() {
        return ts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Graph graph = (Graph) o;

        if (graphId != null ? !graphId.equals(graph.graphId) : graph.graphId != null) return false;
        if (projectCode != null ? !projectCode.equals(graph.projectCode) : graph.projectCode != null) return false;
        if (projectTitle != null ? !projectTitle.equals(graph.projectTitle) : graph.projectTitle != null) return false;
        if (ts != null ? !ts.equals(graph.ts) : graph.ts != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = projectCode != null ? projectCode.hashCode() : 0;
        result = 31 * result + (projectTitle != null ? projectTitle.hashCode() : 0);
        result = 31 * result + (ts != null ? ts.hashCode() : 0);
        result = 31 * result + (graphId != null ? graphId.hashCode() : 0);
        return result;
    }

    public String getGraphId() {
        return graphId;
    }
}
