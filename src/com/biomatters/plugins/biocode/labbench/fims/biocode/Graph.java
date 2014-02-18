package com.biomatters.plugins.biocode.labbench.fims.biocode;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Matthew Cheung
 *         Created on 3/02/14 4:56 PM
 */
@XmlRootElement
public class Graph {
    @XmlElement(name = "expedition_code") public String expeditionCode;
    @XmlElement(name = "expedition_title") public String expeditionTitle;
    @XmlElement(name = "ts") public String ts;
    @XmlElement(name = "graph") public String graphId;

    public Graph() {
    }

    public Graph(String expeditionCode, String expeditionTitle, String ts, String graphId) {
        this.expeditionCode = expeditionCode;
        this.expeditionTitle = expeditionTitle;
        this.ts = ts;
        this.graphId = graphId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Graph graph = (Graph) o;

        if (graphId != null ? !graphId.equals(graph.graphId) : graph.graphId != null) return false;
        if (expeditionCode != null ? !expeditionCode.equals(graph.expeditionCode) : graph.expeditionCode != null) return false;
        if (expeditionTitle != null ? !expeditionTitle.equals(graph.expeditionTitle) : graph.expeditionTitle != null) return false;
        if (ts != null ? !ts.equals(graph.ts) : graph.ts != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = expeditionCode != null ? expeditionCode.hashCode() : 0;
        result = 31 * result + (expeditionTitle != null ? expeditionTitle.hashCode() : 0);
        result = 31 * result + (ts != null ? ts.hashCode() : 0);
        result = 31 * result + (graphId != null ? graphId.hashCode() : 0);
        return result;
    }

    public String getGraphId() {
        return graphId;
    }
}
