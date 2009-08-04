package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyResultsDocument extends AbstractPluginDocument {

    private Map<AnnotatedPluginDocument, List<VerifyResult>> results;

    public VerifyTaxonomyResultsDocument() {
    }

    public VerifyTaxonomyResultsDocument(Map<AnnotatedPluginDocument, List<VerifyResult>> results) {
        this.results = results;
        setFieldValue("name", DocumentUtilities.getUniqueNameForDocument("Verify Taxonomy Results"));
    }

    public String getName() {
        return (String)getFieldValue("name");
    }

    public String getDescription() {
        return null;
    }

    public String toHTML() {
        StringBuilder html = new StringBuilder("<html>");
        html.append(GuiUtilities.getHtmlHead());
        html.append("<table>");
        html.append("<tr><td>Query<td>Hit<td>E-Value");
        for (Map.Entry<AnnotatedPluginDocument, List<VerifyResult>> result : results.entrySet()) {
            html.append("<tr><td>");
            html.append(result.getKey().getName());
            html.append("<td>").append(result.getValue().get(0).document.getFieldValue(DocumentField.DESCRIPTION_FIELD));
            html.append("<td>").append(result.getValue().get(0).eValue);
        }
        html.append("</table></html>");
        return html.toString();
    }

    @Override
    public Element toXML() {
        Element element = super.toXML();
        Element resultsElement = new Element("results");
        for (Map.Entry<AnnotatedPluginDocument, List<VerifyResult>> entry : results.entrySet()) {
            Element resultElement = new Element("result");
            resultElement.addContent(entry.getKey().getURN().toXML("queryUrn"));
            for (VerifyResult verifyResult : entry.getValue()) {
                resultElement.addContent(verifyResult.toXML());
            }
            resultsElement.addContent(resultElement);
        }
        element.addContent(resultsElement);
        return element;
    }

    @Override
    public void fromXML(Element root) throws XMLSerializationException {
        super.fromXML(root);
        results = new HashMap<AnnotatedPluginDocument, List<VerifyResult>>();
        for (Element result : root.getChild("results").getChildren()) {
            AnnotatedPluginDocument queryDoc;
            try {
                queryDoc = DocumentUtilities.getDocumentByURN(URN.fromXML(result.getChild("queryUrn")));
            } catch (MalformedURNException e) {
                throw new XMLSerializationException(e);
            }
            List<VerifyResult> verifyResults = new ArrayList<VerifyResult>();
            for (Element element : result.getChildren("verifyResult")) {
                verifyResults.add(new VerifyResult(element));
            }
            results.put(queryDoc, verifyResults);
        }
    }
}
