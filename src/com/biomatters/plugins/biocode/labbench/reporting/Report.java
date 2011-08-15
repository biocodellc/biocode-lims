package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import org.jdom.Element;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import jebl.util.ProgressListener;

import javax.swing.*;

/**
 * @author Steve
 * @version $Id$
 */
public abstract class Report implements XMLSerializable {

    private static final int version = 1;

    private String name;

    private Options options;
    private Element optionsValues;

    public Report(FimsToLims fimsToLims) {
        this.options = createOptions(fimsToLims);
        optionsValues = options.valuesToXML("Options");
    }

    public Report(Element e) throws XMLSerializationException {
        fromXML(e);
    }

    @Override
    public String toString() {
        return getName();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFimsToLims(FimsToLims fimsToLims) {
        options = createOptions(fimsToLims);
        if(optionsValues != null) {
            options.valuesFromXML(optionsValues);
        }
    }

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    public abstract String getTypeName();

    public abstract String getTypeDescription();

    public abstract Options createOptions(FimsToLims fimsToLims);

    public abstract ReportChart getChart(Options options, FimsToLims fimsToLims, ProgressListener progress)  throws SQLException;


    public static abstract class ReportChart {
        public Options getOptions() {
            return null;
        }
        public abstract JPanel getPanel();
    }

    public Element toXML() {
        Element reportElement = new Element("Report");
        reportElement.setAttribute("version", ""+version);
        reportElement.addContent(new Element("name").setText(name));
        if(options != null) {
            reportElement.addContent(options.valuesToXML("Options"));
        }
        else if(optionsValues != null) {
            reportElement.addContent(((Element)optionsValues.clone()).setName("Options"));
        }
        return reportElement;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        String elementVersion = element.getAttributeValue("version");
        if(version != Integer.parseInt(elementVersion)) {
            throw new XMLSerializationException("Expected version "+version+" but got version "+elementVersion);
        }
        this.name = element.getChildText("name");
        optionsValues = element.getChild("Options");
    }
}
