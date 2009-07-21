package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentType;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentSearchCache;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import java.util.List;
import java.util.Date;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/07/2009 3:06:37 PM
 */
public class PrimerOption extends Options.ComboBoxOption<Options.OptionValue>{
    private OligoSequenceDocument extraPrimer;
    private SimpleListener primerListener;
    private static final Options.OptionValue[] noValue = new Options.OptionValue[] {
            new Options.OptionValue("noValues", "No primers found in your database")
    };

    protected PrimerOption(Element e) throws XMLSerializationException {
        super(e);
        Element extraPrimerElement = e.getChild("ExtraPrimer");
        if(extraPrimerElement != null) {
            extraPrimer = new OligoSequenceDocument(extraPrimerElement.getChildText("name"), "", extraPrimerElement.getChildText("sequence"), new Date());
        }
        init();
    }

    private void init() {
        final DocumentSearchCache<OligoSequenceDocument> searchCache = DocumentSearchCache.getDocumentSearchCacheFor(DocumentType.OLIGO_DOC_TYPE);
        primerListener = new SimpleListener() {
            public void objectChanged() {
                List<AnnotatedPluginDocument> searchCacheDocuments = searchCache.getDocuments();
                List<AnnotatedPluginDocument> documents = searchCacheDocuments == null ? null : new ArrayList<AnnotatedPluginDocument>(searchCacheDocuments);
                if (documents != null) {
                    if (documents.size() == 0 && extraPrimer == null) {
                        Options.OptionValue noPrimer = new Options.OptionValue("noValues", "No primers found in your database");
                        setPossibleValuesReal(Arrays.asList(noPrimer));
                        setDefaultValue(noPrimer);
                    }
                    else {
                        List<Options.OptionValue> valueList = new ArrayList<Options.OptionValue>(getOptionValues(documents));
                        if(extraPrimer != null) {
                            boolean alreadyHasPrimer = false;
                            for(AnnotatedPluginDocument doc : documents) {
                                OligoSequenceDocument seq = (OligoSequenceDocument)doc.getDocumentOrCrash();
                                if(doc.getName().equals(extraPrimer.getName()) && seq.getSequenceString().equalsIgnoreCase(extraPrimer.getSequenceString())) {
                                    alreadyHasPrimer = true;
                                }
                            }
                            if(!alreadyHasPrimer) {
                                valueList.add(new Options.OptionValue(extraPrimer.getName(), extraPrimer.getName(), extraPrimer.getSequenceString()));
                            }
                        }
                        setPossibleValuesReal(valueList);
                        setDefaultValue(valueList.get(0));
                    }
                }
            }
        };
        primerListener.objectChanged();
        searchCache.addDocumentsUpdatedListener(primerListener);
        if(searchCache.hasSearchedEntireDatabase()) {
            primerListener.objectChanged();
        }
    }

    private List<Options.OptionValue> getOptionValues(List<AnnotatedPluginDocument> documents) {
        ArrayList<Options.OptionValue> primerList = new ArrayList<Options.OptionValue>();
        for(AnnotatedPluginDocument doc : documents) {
            OligoSequenceDocument seq = (OligoSequenceDocument)doc.getDocumentOrCrash();
            Options.OptionValue optionValue = getOptionValue(seq, doc.getName());
            primerList.add(optionValue);
        }
        return primerList;
    }

    private Options.OptionValue getOptionValue(OligoSequenceDocument seq, String overrideName) {
        Options.OptionValue optionValue = new Options.OptionValue(overrideName, overrideName, seq.getSequenceString());
        return optionValue;
    }

    @Override
    public Element toXML() {
        Element element = super.toXML();
        Element extraPrimerElement = new Element("ExtraPrimer");
        if(extraPrimer != null) {
            extraPrimerElement.addContent(new Element("name").setText(extraPrimer.getName()));
            extraPrimerElement.addContent(new Element("sequence").setText(extraPrimer.getSequenceString()));
        }
        else {
            Options.OptionValue value = getValue();
            extraPrimerElement.addContent(new Element("name").setText(value.getName()));
            extraPrimerElement.addContent(new Element("sequence").setText(value.getDescription()));
        }
        element.addContent(extraPrimerElement);
        return element;
    }

    protected PrimerOption(String name, String label, OligoSequenceDocument possibleExtraPrimer) {
        super(name, label, noValue, noValue[0]);
        this.extraPrimer = possibleExtraPrimer;
        init();
    }

    private void setPossibleValuesReal(List<? extends Options.OptionValue> possibleValues) {
        super.setPossibleValues(possibleValues);
    }

    @Override
    public void setPossibleValues(List<? extends Options.OptionValue> possibleValues) {
        return;
    }

    @Override
    public void addPossibleValue(Options.OptionValue newValue) {
        return;
    }

    public void setAndAddValue(String name, String sequence) {
        extraPrimer = new OligoSequenceDocument(name, name, sequence, new Date());
        primerListener.objectChanged();
        setValue(getOptionValue(extraPrimer, name));
    }
}
