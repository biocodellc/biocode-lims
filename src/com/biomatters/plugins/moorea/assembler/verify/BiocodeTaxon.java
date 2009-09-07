package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.types.TaxonomyDocument;
import org.jdom.Element;

import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class BiocodeTaxon implements XMLSerializable {

    final String name;
    final Level level;
    final BiocodeTaxon parent;
    static final String ELEMENT_NAME = "BiocodeTaxon";

    public enum Level {
        KINGDOM,
        PHYLUM,
        SUBPHYLUM,
        SUPERCLASS,
        CLASS,
        SUBCLASS,
        INFRACLASS,
        SUPERORDER,
        ORDER,
        SUBORDER,
        INFRAORDER,
        SUPERFAMILY,
        FAMILY,
        SUBFAMILY,
        TRIBE,
        SUBTRIBE,
        GENUS,
        SUBGENUS,
//        SPECIES,
//        SUBSPECIES
    }

    public BiocodeTaxon(Element root) {
        BiocodeTaxon parent = null;
        List<Element> elements = root.getChildren();
        String thisName = null;
        Level thisLevel = null;
        BiocodeTaxon thisParent = null;
        for (int i = 0; i < elements.size(); i++) {
            Element element = elements.get(i);
            String name = element.getText();
            Level level = Level.valueOf(element.getName());
            if (i == elements.size() - 1) {
                thisName = name;
                thisLevel = level;
                thisParent = parent;
                break;
            }
            parent = new BiocodeTaxon(name, level, parent);
        }
        this.name = thisName;
        this.level = thisLevel;
        this.parent = thisParent;
    }

    public BiocodeTaxon(String name, Level level, BiocodeTaxon parent) {
        this.name = name;
        this.level = level;
        this.parent = parent;
    }

    public static BiocodeTaxon fromNcbiTaxon(TaxonomyDocument.Taxon ncbiTaxon) {
        if (ncbiTaxon == null) {
            return null;
        }
        Level level = fromNcbiLevel(ncbiTaxon.getTaxonomicLevel());
        if (level == null) {
            return fromNcbiTaxon(ncbiTaxon.getParent());
        }
        return new BiocodeTaxon(ncbiTaxon.getScientificName(), level, fromNcbiTaxon(ncbiTaxon.getParent()));
    }

    public Element toXML() {
        Element root = new Element(ELEMENT_NAME);
        _ToXml(root);
        return root;
    }

    /**
     *
     * @return eg. "<SUBFAMILY>monkeys</SUBFAMILY>"
     */
    private void _ToXml(Element root) {
        if (parent != null) {
            parent._ToXml(root);
        }
        Element justThisLevel = new Element(level.name());
        justThisLevel.setText(name);
        root.addContent(justThisLevel);
    }

    @Override
    public String toString() {
        if (parent == null) {
            return name;
        }
        return parent.toString() + "; " + name;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        throw new UnsupportedOperationException();
    }

    private static Level fromNcbiLevel(TaxonomyDocument.TaxonomicLevel ncbiLevel) {
        try {
            return Level.valueOf(ncbiLevel.name().toUpperCase());
        } catch (IllegalArgumentException iae) {
            return null;
        } catch (NullPointerException npe) {
            return null;
        }
    }
}
