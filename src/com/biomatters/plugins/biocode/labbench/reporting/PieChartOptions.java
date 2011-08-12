package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.sql.SQLException;

/**
 * @author Steve
 * @version $Id$
 */
public class PieChartOptions extends Options {
    protected static String FIMS_FIELD = "fimsField";


    protected String tissueColumnId;
    static String REACTION_FIELDS = "reactionFields";

    public PieChartOptions(Class cl, FimsToLims fimsToLims) {
        super(cl);
        init(fimsToLims);
        tissueColumnId = fimsToLims.getTissueColumnId();
    }

    public PieChartOptions(Class cl, String preferenceNameSuffix, FimsToLims fimsToLims) {
        super(cl, preferenceNameSuffix);
        init(fimsToLims);
    }

    public PieChartOptions(Element element) throws XMLSerializationException {
        super(element);
        this.tissueColumnId = element.getChildText("tissueColumnId");
    }

    @Override
    public Element toXML() {
        Element element = super.toXML();
        element.addContent(new Element("tissueColumnId").setText(tissueColumnId));
        return element;
    }

    private void init(final FimsToLims fimsToLims) {
        ReactionFieldOptions reactionFieldOptions = new ReactionFieldOptions(this.getClass(), fimsToLims, false);
        addChildOptions(REACTION_FIELDS, "", "", reactionFieldOptions);
        final Options.BooleanOption fimsField = addBooleanOption(FIMS_FIELD, "Restrict by FIMS field", false);
        fimsField.setEnabled(fimsToLims.limsHasFimsValues());
        if(!fimsToLims.limsHasFimsValues()) {
            fimsToLims.addFimsTableChangedListener(new SimpleListener(){
                public void objectChanged() {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            fimsField.setEnabled(fimsToLims.limsHasFimsValues());
                        }
                    };
                    ThreadUtilities.invokeNowOrLater(runnable);
                }
            });
        } 
        SingleFieldOptions fimsOptions = new SingleFieldOptions(fimsToLims.getFimsFields());
        Options fimsMultiOptions = new Options(this.getClass());
        fimsMultiOptions.beginAlignHorizontally("", false);
        fimsMultiOptions.addLabel("Match ");
        Options.OptionValue[] allOrAny = new OptionValue[] {
                new OptionValue("all", "All"),
                new OptionValue("any", "Any")
        };
        fimsMultiOptions.addComboBoxOption("allOrAny", "", allOrAny, allOrAny[0]);
        fimsMultiOptions.addLabel(" of the following:");
        fimsMultiOptions.endAlignHorizontally();
        fimsMultiOptions.addMultipleOptions("fims", fimsOptions, false);
        addChildOptions(FIMS_FIELD, "", "", fimsMultiOptions);
        fimsField.addChildOptionsDependent(fimsMultiOptions, true, true);

    }


    String getLimsSql() {
        ReactionFieldOptions reactionFields = (ReactionFieldOptions)getChildOptions().get(REACTION_FIELDS);
        return reactionFields.getSql((Boolean)getValue(FIMS_FIELD) ? "fims_values f" : null, null);
    }

    String getFimsSql() {
        if((Boolean)getValue(FIMS_FIELD)) {
            Options fimsOptions = getChildOptions().get(FIMS_FIELD);
            MultipleOptions fimsMultipleOptions = fimsOptions.getMultipleOptions("fims");
            List<String> fimsTerms = new ArrayList<String>();
            for(Options fimsOption : fimsMultipleOptions.getValues()) {
                SingleFieldOptions fimsFieldOptions = (SingleFieldOptions)fimsOption;
                fimsTerms.add("f."+FimsToLims.getSqlColName(fimsFieldOptions.getFieldName())+" "+fimsFieldOptions.getComparitor()+" ?");
            }
            return "("+StringUtilities.join("any".equals(fimsOptions.getValueAsString("allOrAny")) ? " OR " : " AND ", fimsTerms)+")";
        }
        return null;
    }

    public List<Object> getFimsValues() {
        Options fimsOptions = getChildOptions().get(FIMS_FIELD);
        MultipleOptions fimsMultipleOptions = fimsOptions.getMultipleOptions("fims");
        List<Object> fimsTerms = new ArrayList<Object>();
        for(Options fimsOption : fimsMultipleOptions.getValues()) {
            SingleFieldOptions fimsFieldOptions = (SingleFieldOptions)fimsOption;
            fimsTerms.add(fimsFieldOptions.getValue());
        }
        return fimsTerms;
    }
}
