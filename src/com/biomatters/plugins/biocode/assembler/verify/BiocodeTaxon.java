package com.biomatters.plugins.biocode.assembler.verify;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.types.TaxonomyDocument;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class BiocodeTaxon implements XMLSerializable {

    final String name;
    final Level level;
    final BiocodeTaxon parent;
    private final List<String> skippedLevels = new ArrayList<String>();

    static final String ELEMENT_NAME = "BiocodeTaxon";
    private static final String SKIPPED_LEVELS_ELEMENT_NAME = "SkippedLevels";

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
        SUBGENUS
    }

    public BiocodeTaxon(Element root) {
        BiocodeTaxon parent = null;
        List<Element> elements = root.getChildren();
        String thisName = null;
        Level thisLevel = null;
        BiocodeTaxon thisParent = null;
        if (elements.get(elements.size() - 1).getName().equals(SKIPPED_LEVELS_ELEMENT_NAME)) {
            Element skippedElement = elements.remove(elements.size() - 1);
            for (Element skippedLevelElement : skippedElement.getChildren()) {
                skippedLevels.add(skippedLevelElement.getText());
            }
        }
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

    public BiocodeTaxon(BiocodeTaxon taxon) {
        this.name = taxon.name;
        this.level = taxon.level;
        this.parent = taxon.parent;
        this.skippedLevels.addAll(taxon.skippedLevels);
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

    public void setSkippedLevels(List<String> skippedLevels) {
        this.skippedLevels.clear();
        this.skippedLevels.addAll(skippedLevels);
    }

    public List<String> getSkippedLevels() {
        return skippedLevels;
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
        if (!skippedLevels.isEmpty()) {
            Element skippedElement = new Element(SKIPPED_LEVELS_ELEMENT_NAME);
            for (String skippedLevel : skippedLevels) {
                Element skippedLevelElement = new Element("Level");
                skippedLevelElement.setText(skippedLevel);
                skippedElement.addContent(skippedLevelElement);
            }
            root.addContent(skippedElement);
        }
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
