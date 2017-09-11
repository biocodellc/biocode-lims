package com.biomatters.plugins.biocode.labbench.fims.biocode;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Matthew Cheung
 *         Created on 3/02/14 4:56 PM
 */
@XmlRootElement
@JsonIgnoreProperties(ignoreUnknown = true)
public class Graph {
    @XmlElement(name = "expeditionCode") public String expeditionCode;
    @XmlElement(name = "expeditionTitle") public String expeditionTitle;
    @XmlElement(name = "ts") public String ts;
    @XmlElement(name = "graph") public String graphId;
    @XmlElement(name = "identifier") String identifier;
    @XmlElement(name= "bcidId") String bcid;
    @XmlElement(name= "projectId") String projectId;
    @XmlElement(name= "webAddress") String webAddress;

    public Graph() {
    }

    public Graph(String expeditionCode, String expeditionTitle, String ts, String graphId, String identifier, String bcid, String projectId, String webAddress, String graph) {
        this.expeditionCode = expeditionCode;
        this.expeditionTitle = expeditionTitle;
        this.ts = ts;
        this.graphId = graphId;
        this.identifier = identifier;
        this.bcid = bcid;
        this.projectId = projectId;
        this.webAddress = webAddress;
    }

    public String getExpeditionCode() {
        return expeditionCode;
    }

    public String getExpeditionTitle() {
        return expeditionTitle;
    }

    public String getTs() {
        return ts;
    }

    public String getGraphId() {
        return graphId;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getBcid() {
        return bcid;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getWebAddress() {
        return webAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Graph graph = (Graph) o;

        if (expeditionCode != null ? !expeditionCode.equals(graph.expeditionCode) : graph.expeditionCode != null)
            return false;
        if (expeditionTitle != null ? !expeditionTitle.equals(graph.expeditionTitle) : graph.expeditionTitle != null)
            return false;
        if (ts != null ? !ts.equals(graph.ts) : graph.ts != null) return false;
        if (graphId != null ? !graphId.equals(graph.graphId) : graph.graphId != null) return false;
        if (identifier != null ? !identifier.equals(graph.identifier) : graph.identifier != null) return false;
        if (bcid != null ? !bcid.equals(graph.bcid) : graph.bcid != null) return false;
        if (projectId != null ? !projectId.equals(graph.projectId) : graph.projectId != null) return false;
        if (webAddress != null ? !webAddress.equals(graph.webAddress) : graph.webAddress != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = expeditionCode != null ? expeditionCode.hashCode() : 0;
        result = 31 * result + (expeditionTitle != null ? expeditionTitle.hashCode() : 0);
        result = 31 * result + (ts != null ? ts.hashCode() : 0);
        result = 31 * result + (graphId != null ? graphId.hashCode() : 0);
        result = 31 * result + (identifier != null ? identifier.hashCode() : 0);
        result = 31 * result + (bcid != null ? bcid.hashCode() : 0);
        result = 31 * result + (projectId != null ? projectId.hashCode() : 0);
        result = 31 * result + (webAddress != null ? webAddress.hashCode() : 0);
        return result;
    }
}
