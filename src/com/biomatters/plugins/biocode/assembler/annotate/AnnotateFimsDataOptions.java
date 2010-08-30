package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.options.NamePartOption;
import com.biomatters.plugins.biocode.options.NameSeparatorOption;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;


public class AnnotateFimsDataOptions extends Options {

    public AnnotateFimsDataOptions()throws DocumentOperationException{
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException("You must connect to Biocode to annotate with FIMS");
        }
        FIMSConnection fimsConnection = BiocodeService.getInstance().getActiveFIMSConnection();
        boolean hasPlateInfo = fimsConnection.canGetTissueIdsFromFimsTissuePlate();
        List<OptionValue> matchableFields = convertDocumentFieldsToOptionValues(fimsConnection.getSearchAttributes());

        beginAlignHorizontally("", false);
        addCustomOption(new NamePartOption("namePart", "Match"));
        addLabel("part of name,");
        addCustomOption(new NameSeparatorOption("nameSeparator", "separated by"));

        if(hasPlateInfo){
            List<OptionValue> fieldOptions = new ArrayList<OptionValue>();
            fieldOptions.add(new OptionValue("plate", "Plate"));
            fieldOptions.add(new OptionValue("field", "Field"));
        }


    }

    private static List<OptionValue> convertDocumentFieldsToOptionValues(List<DocumentField> fields) {
        List<OptionValue> values = new ArrayList<OptionValue>();
        for(DocumentField field : fields) {
            values.add(new OptionValue(field.getCode(), field.getName(), field.getDescription()));
        }
        return values;
    }

    public AnnotateFimsDataOptions(Element e) throws XMLSerializationException{
        super(e);
    }


}
