package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 14/07/2009 7:15:35 PM
 */
public abstract class ReactionOptions<T extends Reaction> extends Options {

    public static final String RUN_STATUS = "runStatus";
    public static final String WORKFLOW_ID = "workflowId";
    public static final String COCKTAIL_OPTION_ID = "cocktail";
    public static final OptionValue NOT_RUN_VALUE = new OptionValue("not run", "not run");
    public static final OptionValue RUN_VALUE = new OptionValue("run", "run");
    public static final OptionValue PASSED_VALUE = new OptionValue("passed", "passed");
    public static final OptionValue SUSPECT_VALUE = new OptionValue("suspect", "suspect");
    public static final OptionValue FAILED_VALUE = new OptionValue("failed", "failed");
    public static final OptionValue[] STATUS_VALUES = new OptionValue[] { NOT_RUN_VALUE, RUN_VALUE, PASSED_VALUE, SUSPECT_VALUE, FAILED_VALUE };
    public static final String[] SAMPLE_LOCI = new String[] {"None", "COI", "16s", "18s", "ITS", "ITS1", "ITS2", "28S", "12S", "rbcl", "matK", "trnH-psba", "cytB"};
    protected T reaction;

    public ReactionOptions() {
    }

    public ReactionOptions(Class cl) {
        super(cl);
    }

    public ReactionOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
    }

    public ReactionOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    public abstract boolean fieldIsFinal(String fieldCode);

    public abstract void refreshValuesFromCaches();

    public abstract Cocktail getCocktail();

    public void setReaction(T r) {
        this.reaction = r;
    }
}
