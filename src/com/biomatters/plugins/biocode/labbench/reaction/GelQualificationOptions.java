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
public class GelQualificationOptions extends ReactionOptions {

    static final String EXTRCTION_ID = "extractionId";
    private static final String TISSUE_ID = "tissueId";

    public GelQualificationOptions() {
        addStringOption(TISSUE_ID, "Tissue ID", "");
        addStringOption(EXTRCTION_ID, "Extraction ID", "");
        addDateOption("date", "Date", new Date());
        addStringOption("technician", "Technician", "", "May be blank");
        TextAreaOption notes = new TextAreaOption("notes", "Notes", "");
        addCustomOption(notes);

        Options.IntegerOption volume = addIntegerOption("volume", "Extraction Volume", 5, 0, Integer.MAX_VALUE);
        volume.setUnits('\u00B5' + "L");
    }

    public GelQualificationOptions(Element e) throws XMLSerializationException {
        super(e);
    }

    private static final List<String> FINAL_FIELDS = Arrays.asList(TISSUE_ID);


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
