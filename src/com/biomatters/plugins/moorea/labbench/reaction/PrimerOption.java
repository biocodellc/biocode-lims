package com.biomatters.plugins.moorea.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentSearchCache;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotation;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotationInterval;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentType;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.SequenceUtilities;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/07/2009 3:06:37 PM
 */
public class PrimerOption extends Options.ComboBoxOption<Options.OptionValue>{
    private OligoSequenceDocument extraPrimer;
    private SimpleListener primerListener;

    public static final Options.OptionValue NO_PRIMER_VALUE = new Options.OptionValue("No Primer", "No Primer", "");

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
                    List<Options.OptionValue> valueList = new ArrayList<Options.OptionValue>();
                    valueList.add(NO_PRIMER_VALUE);
                    valueList.addAll(getOptionValues(documents));
                    if(documents.size() > 0 || extraPrimer != null) {
                        if(extraPrimer != null) {
                            boolean alreadyHasPrimer = NO_PRIMER_VALUE.getName().equals(extraPrimer.getName()) && NO_PRIMER_VALUE.getDescription().equals(extraPrimer.getDescription());
                            Options.OptionValue extraPrimerValue = getOptionValue(extraPrimer, extraPrimer.getName());
                            for(AnnotatedPluginDocument doc : documents) {
                                OligoSequenceDocument seq = (OligoSequenceDocument)doc.getDocumentOrCrash();
                                Options.OptionValue seqValue = getOptionValue(seq, doc.getName());
                                if(seqValue.getName().equals(extraPrimerValue.getName()) && seqValue.getDescription().equalsIgnoreCase(extraPrimerValue.getDescription())) {
                                    alreadyHasPrimer = true;
                                }
                            }
                            if(!alreadyHasPrimer) {
                                valueList.add(getOptionValue(extraPrimer, extraPrimer.getName()));
                            }
                        }
                    }
                    setPossibleValuesReal(valueList);
                    setDefaultValue(NO_PRIMER_VALUE);
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
        String originalSequenceString = seq.getSequenceString();

        SequenceAnnotation annotation = getOligoAnnotationIfValid(seq);

        String primerSeqString;
        if(annotation != null) {
            boolean isPrimerAnnotation =
                    annotation.getType().equals(SequenceAnnotation.TYPE_PRIMER_BIND) ||
                    annotation.getType().equals(SequenceAnnotation.TYPE_PRIMER_BIND_REVERSE) ||
                    annotation.getType().equals(SequenceAnnotation.TYPE_DNA_PROBE);
            if(!isPrimerAnnotation || annotation.getIntervals().size() != 1) {
                return null;
            }

            int from = annotation.getIntervals().get(0).getFrom();
            int to = annotation.getIntervals().get(0).getTo();

            // If the user has created his/her own primer_bind annotations then they can be in the reverse
            // direction, so we have to deal with that when getting the annotation sequence.
            boolean reversed = false;
            if(from > to) {
                int temp = from;
                from = to;
                to = temp;
                reversed = true;
            }

            if (!seq.isCircular()) {
                if (from < 1 || to > originalSequenceString.length()) {
                    return null;  // Part of the primer is missing....
                }
            }

            String sequenceQualifierValue = annotation.getQualifierValue("Sequence"); //the primer sequence may actually be different to the target sequence (eg. due to mismatches).
            if(sequenceQualifierValue != null && sequenceQualifierValue.length() > 0) {
                primerSeqString = sequenceQualifierValue;
            } else {
                if(to > originalSequenceString.length()){
                    primerSeqString = originalSequenceString.substring(from - 1) + originalSequenceString.substring(0, to - originalSequenceString.length());
                } else {
                    primerSeqString = originalSequenceString.substring(from - 1, to);
                }
                if(reversed){
                    primerSeqString = jebl.evolution.sequences.Utils.reverseComplement(primerSeqString);
                }
            }

        } else {
            primerSeqString = originalSequenceString;
        }
        Options.OptionValue optionValue = new Options.OptionValue(overrideName, overrideName, primerSeqString);
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

    protected PrimerOption(String name, String label) {
        super(name, label, noValue, noValue[0]);
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

    //taken from ExistingOligoOptionValue
    private static SequenceAnnotation getOligoAnnotationIfValid(OligoSequenceDocument originalDoc) {
        SequenceAnnotation correctOligo = null;
        List<SequenceAnnotation> oligoAnnotations = new ArrayList<SequenceAnnotation>();
        oligoAnnotations.addAll(SequenceUtilities.getAnnotationsOfType(originalDoc.getSequenceAnnotations(), SequenceAnnotation.TYPE_PRIMER_BIND));
        oligoAnnotations.addAll(SequenceUtilities.getAnnotationsOfType(originalDoc.getSequenceAnnotations(), SequenceAnnotation.TYPE_PRIMER_BIND_REVERSE));
        oligoAnnotations.addAll(SequenceUtilities.getAnnotationsOfType(originalDoc.getSequenceAnnotations(), SequenceAnnotation.TYPE_DNA_PROBE));
        for (SequenceAnnotation annotation : oligoAnnotations) {
            SequenceAnnotationInterval interval = annotation.getIntervals().get(0);
            if(interval.getLength() == originalDoc.getSequenceLength() && interval.getMinimumIndex() == 1 &&
                    interval.getMaximumIndex() == originalDoc.getSequenceLength()) {
                if (correctOligo != null && (!correctOligo.getType().equals(annotation.getType()) ||
                        interval.getDirection() != correctOligo.getIntervals().get(0).getDirection())) {
                    return null; //found another matching primer annotation of a different type or in a different direction
                }
                correctOligo = annotation;
            }
        }
        return correctOligo;
    }
}
