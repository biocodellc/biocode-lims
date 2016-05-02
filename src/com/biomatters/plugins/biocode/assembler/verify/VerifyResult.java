package com.biomatters.plugins.biocode.assembler.verify;

import com.biomatters.geneious.publicapi.documents.*;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Richard
*/
class VerifyResult implements XMLSerializable {

    final List<AnnotatedPluginDocument> hitDocuments;
    final AnnotatedPluginDocument queryDocument;
    final BiocodeTaxon queryTaxon;

    VerifyResult(List<AnnotatedPluginDocument> hitDocuments, AnnotatedPluginDocument queryDocument, BiocodeTaxon queryTaxon) {
        this.hitDocuments = hitDocuments;
        this.queryDocument = queryDocument;
        this.queryTaxon = queryTaxon;
    }

    VerifyResult(Element element) throws XMLSerializationException {
        try {
            hitDocuments = new ArrayList<AnnotatedPluginDocument>();
            Element hitsElement = element.getChild("hits");
            if (hitsElement == null) {
                throw new XMLSerializationException("This verify results document is no longer supported, run Verify Taxonomy again.");
            }
            for (Element hitElement : hitsElement.getChildren()) {
                if (hitElement.getChild("hitUrn") != null) {
                    hitElement = hitElement.getChild("hitUrn"); //old serialization style
                }
                AnnotatedPluginDocument hitDocument = DocumentUtilities.getDocumentByURN(URN.fromXML(hitElement));
                this.hitDocuments.add(hitDocument);
            }
        } catch (MalformedURNException e) {
            throw new XMLSerializationException(e);
        }
        try {
            this.queryDocument = DocumentUtilities.getDocumentByURN(URN.fromXML(element.getChild("queryUrn")));
        } catch (MalformedURNException e) {
            throw new XMLSerializationException(e);
        }
        Element queryTaxonElement = element.getChild(BiocodeTaxon.ELEMENT_NAME);
        if (queryTaxonElement != null) {
            queryTaxon = new BiocodeTaxon(queryTaxonElement);
        } else {
            queryTaxon = null;
        }
    }

    void addHit(AnnotatedPluginDocument hitDocument) {
        hitDocuments.add(hitDocument);
    }

    public Element toXML() {
        Element element = new Element("verifyResult");
        element.addContent(queryDocument.getURN().toXML("queryUrn"));
        Element hitsElement = new Element("hits");
        for (AnnotatedPluginDocument hitDocument : hitDocuments) {
            hitsElement.addContent(hitDocument.getURN().toXML("hit"));
        }
        element.addContent(hitsElement);
        if (queryTaxon != null) {
            element.addContent(queryTaxon.toXML());
        }
        return element;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        throw new UnsupportedOperationException("");
    }
}
