package com.biomatters.plugins.biocode.labbench.reaction;

import org.jdom.Element;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.TextAreaOption;

import java.util.Date;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 14/07/2009 7:46:04 PM
 */
public class ExtractionOptions extends ReactionOptions{
    public boolean fieldIsFinal(String fieldCode) {
        return false;//"sampleId".equals(fieldCode) || "extractionId".equals(fieldCode);
    }

    public ExtractionOptions() {
        addStringOption("sampleId", "Tissue Sample Id", "");
        addStringOption("extractionId", "Extraction Id", "");
        addDateOption("date", "Date", new Date());          
        addStringOption("extractionBarcode", "Extraction Barcode", "", "May be blank");
        addStringOption("extractionMethod", "Extraction Method", "");
        addStringOption("parentExtraction", "Parent Extraction Id", "", "May be blank");
        addStringOption("previousPlate", "Previous Plate", "", "May be blank");
        addStringOption("previousWell", "Previous Well", "", "May be blank");
        addIntegerOption("dilution", "Dilution 1/", 5, 0, Integer.MAX_VALUE);
        Options.IntegerOption volume = addIntegerOption("volume", "Extraction Volume", 5, 0, Integer.MAX_VALUE);
        volume.setUnits("uL");
        addStringOption("technician", "Technician", "", "May be blank");
        TextAreaOption notesOption = new TextAreaOption("notes", "Notes", "");
        addCustomOption(notesOption);
    }

    public void refreshValuesFromCaches() {}

    public ExtractionOptions(Class cl) {
        super(cl);
    }

    public ExtractionOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
    }

    public ExtractionOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    @Override
    public Element toXML() {
        return super.toXML();
    }

    public Cocktail getCocktail() {
        return null;
    }
}
