package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyResultsDocument extends AbstractPluginDocument {

    private Map<AnnotatedPluginDocument, List<VerifyResult>> results;

    public VerifyTaxonomyResultsDocument() {
    }

    public VerifyTaxonomyResultsDocument(Map<AnnotatedPluginDocument, List<VerifyResult>> results, String keywords) {
        this.results = results;
        setFieldValue("name", DocumentUtilities.getUniqueNameForDocument("Verify Taxonomy Results"));
        setFieldValue("keywords", keywords);
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
        html.append("<tr><td><b>Query</b><td><b>Query Taxon</b><td><b>Hit Taxon</b><td><b>Keywords</b><td><b>Hit Definition</b><td><b>E-Value</b><td><b>Bin</b>");
        for (Map.Entry<AnnotatedPluginDocument, List<VerifyResult>> result : results.entrySet()) {

            AtomicReference<String> keys = new AtomicReference<String>(getFieldValue("keywords").toString());
            AtomicReference<String> definition = new AtomicReference<String>(result.getValue().get(0).document.getFieldValue(DocumentField.DESCRIPTION_FIELD).toString());
            Object fimsTaxonomy = result.getKey().getFieldValue(DocumentField.TAXONOMY_FIELD);
            AtomicReference<String> taxonomy = new AtomicReference<String>(fimsTaxonomy == null ? "" : fimsTaxonomy.toString());
            AtomicReference<String> blastTaxonomy = new AtomicReference<String>(result.getValue().get(0).document.getFieldValue(DocumentField.TAXONOMY_FIELD).toString());
            boolean allOk = highlight(keys, ",", definition);
            allOk &= highlight(taxonomy, ";", blastTaxonomy);

            String nameColor = allOk ? "green" : "red";

            html.append("<tr>");

            html.append("<td valign=\"top\"><font color=\"").append(nameColor).append("\">").append(result.getKey().getName()).append("</font>");
            html.append("<td valign=\"top\">").append(taxonomy.get());
            html.append("<td valign=\"top\">").append(blastTaxonomy.get());
            html.append("<td valign=\"top\">").append(keys.get());
            html.append("<td valign=\"top\">").append(definition.get());
            html.append("<td valign=\"top\">").append(result.getValue().get(0).eValue);
            html.append("<td valign=\"top\">").append(result.getKey().getFieldValue(DocumentField.BIN).toString()
                    .replace("</html>", "").replaceAll("<html>.*</head>", "").replaceAll("</?b>", ""));
        }
        html.append("</table></html>");
        return html.toString();
    }

    /**
     *
     * @param keywords keywords separated by delimiter, used to return value too
     * @param delimiter
     * @param s string to check for keywords, used to return value too
     * @return true iff all keywords were found in s, false otherwise
     */
    private static boolean highlight(AtomicReference<String> keywords, String delimiter, AtomicReference<String> s) {
        boolean foundAll = true;
        String[] keys = keywords.get().split(delimiter);
        String keys2 = keywords.get();
        String s2 = s.get();
        for (String key : keys) {
            key = key.trim();
            if (!s2.contains(key)) {
                keys2 = keys2.replace(key, "<font color=\"red\">" + key + "</font>");
                foundAll = false;
            } else {
                keys2 = keys2.replace(key, "<font color=\"green\">" + key + "</font>");
                s2 = s2.replace(key, "<font color=\"green\">" + key + "</font>");
            }
        }
        keywords.set(keys2);
        s.set(s2);
        return foundAll;
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
