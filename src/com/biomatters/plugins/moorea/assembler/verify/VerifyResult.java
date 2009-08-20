package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.documents.*;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Richard
* @version $Id$
*/
class VerifyResult implements XMLSerializable {

    final List<AnnotatedPluginDocument> hitDocuments;
    final AnnotatedPluginDocument queryDocument;

    VerifyResult(List<AnnotatedPluginDocument> hitDocuments, AnnotatedPluginDocument queryDocument) {
        this.hitDocuments = hitDocuments;
        this.queryDocument = queryDocument;
    }

    VerifyResult(Element element) throws XMLSerializationException {
        try {
            hitDocuments = new ArrayList<AnnotatedPluginDocument>();
            Element hitsElement = element.getChild("hits");
            if (hitsElement == null) {
                throw new XMLSerializationException("This verify results document is no longer supported, run Verify Taxonomy again.");
            }
            for (Element hitElement : hitsElement.getChildren()) {
                AnnotatedPluginDocument hitDocument = DocumentUtilities.getDocumentByURN(URN.fromXML(hitElement.getChild("hitUrn")));
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
    }

    void addHit(AnnotatedPluginDocument hitDocument) {
        hitDocuments.add(hitDocument);
    }

    public Element toXML() {
        Element element = new Element("verifyResult");
        element.addContent(queryDocument.getURN().toXML("queryUrn"));
        Element hitsElement = new Element("hits");
        for (AnnotatedPluginDocument hitDocument : hitDocuments) {
            Element hitElement = new Element("hit");
            hitElement.addContent(hitDocument.getURN().toXML("hitUrn"));
            hitsElement.addContent(hitElement);
        }
        element.addContent(hitsElement);
        return element;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        throw new UnsupportedOperationException("");
    }
}
