package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.TextAreaOption;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 12/11/15 1:31 PM
 */
public class GelQuantificationOptions extends ReactionOptions {

    private static final String TISSUE_ID = "tissueId";
    private static final String PARENT_EXTRACTION_ID = "parentExtractionId";

    public GelQuantificationOptions() {
        addStringOption(TISSUE_ID, "Tissue ID", "");
        addStringOption(PARENT_EXTRACTION_ID, "Parent Extraction ID", "");
        addStringOption(Reaction.EXTRACTION_FIELD.getCode(), Reaction.EXTRACTION_FIELD.getName(), "");
        addDateOption("date", "Date", new Date());

        addStringOption("gelBuffer", "Gel Buffer", "");
        addDoubleOption("gelConc", "Gel Concentration", 0.0).setUnits("%");

        addStringOption("stain", "Gel Stain", "");
        addStringOption("stainConc", "Gel Stain Concentration", "");
        addStringOption("stainMethod", "Gel Stain Method", "");

        Options.IntegerOption volume = addIntegerOption("volume", "Extraction Volume", 5, 0, Integer.MAX_VALUE);
        volume.setUnits('\u00B5' + "L");
        addStringOption("gelLadder", "Gel Ladder", "");
        addIntegerOption("threshold", "DNA Threshold", 0).setUnits("bp");
        addIntegerOption("aboveThreshold", "Above Threshold", 0).setUnits("%");

        addStringOption("technician", "Technician", "", "May be blank");
        TextAreaOption notes = new TextAreaOption("notes", "Notes", "");
        addCustomOption(notes);

    }

    public GelQuantificationOptions(Element e) throws XMLSerializationException {
        super(e);
    }

    private static final List<String> FINAL_FIELDS = Arrays.asList(TISSUE_ID, PARENT_EXTRACTION_ID);


    @Override
    public boolean fieldIsFinal(String fieldCode) {
        return FINAL_FIELDS.contains(fieldCode);
    }

    @Override
    public void refreshValuesFromCaches() {

    }

    @Override
    public Cocktail getCocktail() {
        return null;
    }
}
