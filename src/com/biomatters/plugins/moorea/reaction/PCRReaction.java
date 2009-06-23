package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentSearchCache;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentType;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.plugins.moorea.ButtonOption;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.plugins.moorea.TransactionException;
import com.biomatters.plugins.moorea.Workflow;
import org.virion.jam.util.SimpleListener;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 16/05/2009
 * Time: 10:56:30 AM
 * To change this template use File | Settings | File Templates.
 */
public class PCRReaction extends Reaction {

    private Options options;

    public PCRReaction() {
        options = new PCROptions(this.getClass());
    }

    public PCRReaction(ResultSet r, Workflow workflow) throws SQLException{
        this();
        setWorkflow(workflow);
        setPlate(r.getInt("pcr.plate"));
        Options options = getOptions();
        options.setValue("extractionId", r.getString("pcr.extractionId"));
        options.setValue("workflowId", r.getString("pcr.workflow"));

        options.getOption("runStatus").getValueFromString(r.getString("pcr.progress"));

        Options.ComboBoxOption primerOption = (Options.ComboBoxOption)options.getOption(PCROptions.PRIMER_OPTION_ID);
        String primerName = r.getString("pcr.prName");
        PCROptions.PrimerOptionValue value = new PCROptions.PrimerOptionValue(primerName, primerName, r.getString("pcr.prSequence"));
        primerOption.setValue(value);//todo: what if the user doesn't have the primer?
        options.setValue("cocktail", r.getString("pcr.cocktail"));
    }

    public Element toXML() {
        return new Element("PCRReaction");
        //todo:
    }

    public void fromXML(Element element) throws XMLSerializationException {
        //todo:
    }

    public Options getOptions() {
        return options;
    }

    public Type getType() {
        return Type.PCR;
    }

    public List<DocumentField> getDefaultDisplayedFields() {
        return Arrays.asList(new DocumentField[] {
                new DocumentField("Tissue ID", "", "tissueId", String.class, true, false),
                new DocumentField("Primer", "", "primer", String.class, true, false),
                new DocumentField("Reaction Cocktail", "", "cocktail", String.class, true, false)
        });
    }

    public String areReactionsValid(List<Reaction> reactions) {
        return null;
    }

    public Color _getBackgroundColor() {
        String runStatus = options.getValueAsString("runStatus");
        if(runStatus.equals("none"))
                return Color.white;
        else if(runStatus.equals("passed"))
                return Color.green.darker();
        else if(runStatus.equals("failed"))
            return Color.red.darker();
        return Color.white;
    }

    public String toSql() throws IllegalStateException {
        throw new IllegalStateException("Not Implemented!");
    }
}
