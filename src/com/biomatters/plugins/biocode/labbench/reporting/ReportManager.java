package com.biomatters.plugins.biocode.labbench.reporting;

import org.jdom.input.SAXBuilder;
import org.jdom.JDOMException;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.List;
import java.util.ArrayList;

import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 10/08/2011 7:59:49 PM
 */


public class ReportManager {
    private File reportXmlFile;
    private List<Report> reports;

    public ReportManager(File userDataDirectory) {
        reportXmlFile = new File(userDataDirectory, "reports.xml");
        reports = new ArrayList<Report>();
    }

    public void loadReportsFromDisk(FimsToLims fimsToLims) {
        reports = new ArrayList<Report>();
        if(reportXmlFile.exists()) {
            SAXBuilder builder = new SAXBuilder();
            try {
                Element reportElement = builder.build(reportXmlFile).detachRootElement();
                for(Element e : reportElement.getChildren("Report")) {
                    Report report = XMLSerializer.classFromXML(e, Report.class);
                    report.setFimsToLims(fimsToLims);
                    reports.add(report);
                }
            } catch (JDOMException e) {
                BiocodeUtilities.displayExceptionDialog("Could not load report", "Geneious could not load one or more of your saved Biocode reports. "+e.getMessage(), e, null);
            } catch (IOException e) {
                BiocodeUtilities.displayExceptionDialog("Could not load report", "Geneious could not load one or more of your saved Biocode reports. "+e.getMessage(), e, null);
            } catch (XMLSerializationException e) {
                BiocodeUtilities.displayExceptionDialog("Could not load report", "Geneious could not load one or more of your saved Biocode reports. "+e.getMessage(), e, null);
            }
        }
    }

    public void addReport(Report report) throws IOException {
        reports.add(report);
        saveReportsToDisk();
    }

    private void saveReportsToDisk() throws IOException {
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        Element reportElement = new Element("Reports");
        for(Report report : reports) {
            reportElement.addContent(XMLSerializer.classToXML("Report", report));
        }
        FileOutputStream out = new FileOutputStream(reportXmlFile);
        outputter.output(reportElement, out);
        out.close();
    }

    public List<Report> getReports() {
        return reports;
    }

    public void setReport(int index, Report report) throws IOException{
        reports.set(index, report);
        saveReportsToDisk();
    }

    public void removeReport(int index) throws IOException {
        if(reports.size() == 0 || index < 0 || index >= reports.size()) {
            return;
        }
        reports.remove(index);
        saveReportsToDisk();
    }
}
