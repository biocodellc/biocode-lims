package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.implementations.EValue;
import org.jdom.Element;

/**
 * @author Richard
* @version $Id$
*/
class VerifyResult implements XMLSerializable {

    final AnnotatedPluginDocument document;
    final EValue eValue;

    VerifyResult(AnnotatedPluginDocument document, EValue eValue) {
        this.document = document;
        this.eValue = eValue;
    }

    VerifyResult(Element element) throws XMLSerializationException {
        try {
            this.document = DocumentUtilities.getDocumentByURN(URN.fromXML(element.getChild("resultUrn")));
        } catch (MalformedURNException e) {
            throw new XMLSerializationException(e);
        }
        this.eValue = new EValue();
        this.eValue.fromXML(element.getChild("evalue"));
    }

    public Element toXML() {
        Element element = new Element("verifyResult");
        element.addContent(eValue.toXML());
        element.addContent(document.getURN().toXML("resultUrn"));
        return element;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        throw new UnsupportedOperationException("");
    }
}
