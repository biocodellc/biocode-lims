package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.PCROptions;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Steve
 * @version $Id$
 */
public class PieChartOptions extends Options {
    protected static String FIMS_FIELD = "fimsField";


    protected String tissueColumnId;
    static String REACTION_FIELDS = "reactionFields";
    private boolean isLocalLims;

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
        isLocalLims = fimsToLims.getLimsConnection().isLocal();
        final ReactionFieldOptions reactionFieldOptions = new ReactionFieldOptions(this.getClass(), fimsToLims, false, false, true, true);
        addChildOptions(REACTION_FIELDS, "", "", reactionFieldOptions);
        final Options.BooleanOption fimsField = addBooleanOption(FIMS_FIELD, "Restrict by Reaciton or FIMS field", false);
        fimsField.setDefaultValue(false);
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
        List<DocumentField> fields = new ArrayList<DocumentField>();
        List<DocumentField> limsSearchFields = new ArrayList<DocumentField>(LIMSConnection.getSearchAttributes());
        limsSearchFields.remove(LIMSConnection.PLATE_TYPE_FIELD);
        limsSearchFields.remove(LIMSConnection.PLATE_DATE_FIELD);
        limsSearchFields.remove(LIMSConnection.PLATE_NAME_FIELD);
        limsSearchFields.add(new DocumentField("Primer", "PCR Primer", "pcr."+PCROptions.PRIMER_OPTION_ID, String.class, false, false));
        fields.addAll(limsSearchFields);
        if(fimsToLims.limsHasFimsValues()) {
            fields.addAll(fimsToLims.getFimsFields());
        }
        SingleFieldOptions fimsOptions = new SingleFieldOptions(fields);
        final Options fimsMultiOptions = new Options(this.getClass());
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

        SimpleListener fimsFieldListener = new SimpleListener() {
            public void objectChanged() {
                MultipleOptions multipleOptions = fimsMultiOptions.getMultipleOptions("fims");
                for (Options options : multipleOptions.getValues()) {
                    SingleFieldOptions fieldOptions = (SingleFieldOptions) options;
                    fieldOptions.setFields(ReportGenerator.getFieldValues(fimsToLims), reactionFieldOptions.getReactionType());
                }
                ((SingleFieldOptions)multipleOptions.getMasterOptions()).setFields(ReportGenerator.getFieldValues(fimsToLims), reactionFieldOptions.getReactionType());
            }
        };
        reactionFieldOptions.addChangeListener(fimsFieldListener);
        fimsFieldListener.objectChanged();

    }



    public String getReactionTable() {
        ReactionFieldOptions reactionFields = (ReactionFieldOptions)getChildOptions().get(REACTION_FIELDS);
        return reactionFields.getTable();
    }


    String getLimsSql() {
        ReactionFieldOptions reactionFields = (ReactionFieldOptions)getChildOptions().get(REACTION_FIELDS);
        return reactionFields.getSql(getTableAndField(), requiresFimsTable() ? Arrays.asList(FimsToLims.FIMS_VALUES_TABLE+" f") : null, false, getExtraSql());
    }

    private Boolean requiresFimsTable() {
        Boolean isRestricting = (Boolean) getValue(FIMS_FIELD);
        if(!isRestricting) {
            return false;
        }
        Options fimsOptions = getChildOptions().get(FIMS_FIELD);
        MultipleOptions fimsMultipleOptions = fimsOptions.getMultipleOptions("fims");
        List<String> fimsTerms = new ArrayList<String>();
        for(Options fimsOption : fimsMultipleOptions.getValues()) {
            SingleFieldOptions fimsFieldOptions = (SingleFieldOptions)fimsOption;
            if(isFimsField(fimsFieldOptions.getFieldName())) {
                return true;
            }
        }
        return false;
    }

    String getTableAndField() {
        ReactionFieldOptions reactionFields = (ReactionFieldOptions)getChildOptions().get(REACTION_FIELDS);
        return ReportGenerator.getTableFieldName(reactionFields.getTable(), reactionFields.getField());
    }

    private boolean isFimsField(String fieldName) {
        return BiocodeService.getInstance().getReportingService().getReportGenerator().fimsToLims.getFimsOrLimsField(fieldName) != null;
    }

    String getExtraSql() {
        if((Boolean)getValue(FIMS_FIELD)) {
            Options fimsOptions = getChildOptions().get(FIMS_FIELD);
            MultipleOptions fimsMultipleOptions = fimsOptions.getMultipleOptions("fims");
            List<String> fimsTerms = new ArrayList<String>();
            boolean usesFims = false;
            for(Options fimsOption : fimsMultipleOptions.getValues()) {
                SingleFieldOptions fimsFieldOptions = (SingleFieldOptions)fimsOption;

                boolean fimsField = isFimsField(fimsFieldOptions.getFieldName());
                usesFims |= fimsField;
                String table = fimsField ?
                        "f."+FimsToLims.getSqlColName(fimsFieldOptions.getFieldName(), isLocalLims)
                        :
                        ReportGenerator.getTableFieldName(getReactionTable(), fimsFieldOptions.getFieldName());
                fimsTerms.add(table+" "+fimsFieldOptions.getComparitor()+" ?");
            }
            String beginning = usesFims ? "f." + tissueColumnId + " = extraction.sampleId AND " : "";
            return beginning + "(" + StringUtilities.join("any".equals(fimsOptions.getValueAsString("allOrAny")) ? " OR " : " AND ", fimsTerms) + ")";
        }
        return null;
    }

    public List<Object> getExtraValues() {
        Options fimsOptions = getChildOptions().get(FIMS_FIELD);
        MultipleOptions fimsMultipleOptions = fimsOptions.getMultipleOptions("fims");
        List<Object> fimsTerms = new ArrayList<Object>();
        for(Options fimsOption : fimsMultipleOptions.getValues()) {
            SingleFieldOptions fimsFieldOptions = (SingleFieldOptions)fimsOption;
            fimsTerms.add("%"+fimsFieldOptions.getValue()+"%");
        }
        return fimsTerms;
    }
}
