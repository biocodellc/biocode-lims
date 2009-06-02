package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.plugin.Options;

import javax.swing.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 22/05/2009
 * Time: 4:57:46 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class ReactionOptions<T extends Reaction> extends Options {


    public abstract void setValuesFromReactions(List<T> reactions);

    public abstract List<T> getReactions();

    public JPanel createPanel() {
        OptionsPanel panel = new OptionsPanel();

        for(Option option : getOptions()) {

        }
        return null;

    }

}
