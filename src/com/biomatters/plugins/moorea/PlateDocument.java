package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.plugins.moorea.plates.Plate;
import com.biomatters.plugins.moorea.plates.PlateView;
import com.biomatters.plugins.moorea.reaction.Reaction;
import com.biomatters.plugins.moorea.reaction.Cocktail;
import com.biomatters.plugins.moorea.reaction.ThermocycleEditor;

import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.awt.*;

import org.jdom.Element;

import javax.swing.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 18/06/2009 4:10:35 PM
 */
public class PlateDocument extends MuitiPartDocument {

    private PlatePart platePart;

    public PlateDocument() {}

    public PlateDocument(Element e) throws XMLSerializationException{
        fromXML(e);
    }

    public PlateDocument(Plate p) {
        platePart = new PlatePart(p);
    }

    public List<DocumentField> getDisplayableFields() {
        return null;
    }

    public Object getFieldValue(String fieldCodeName) {
        return null;
    }

    public String getName() {
        return platePart.getName();
    }

    public URN getURN() {
        return null;
    }

    public Date getCreationDate() {
        return null;
    }

    public String getDescription() {
        return "A "+platePart.getPlate().getReactionType().toString()+" plate.";
    }

    public String toHTML() {
        return null;
    }

    public Element toXML() {
        return platePart.getPlate().toXML();
    }

    public void fromXML(Element element) throws XMLSerializationException {
        Plate plate = new Plate(element);
        platePart = new PlatePart(plate);
    }

    public int getNumberOfParts() {
        return 1;
    }

    public Part getPart(int index) {
        return platePart;
    }


    public static class PlatePart extends Part {
        private Plate plate;
        private PlateView pView;

        public PlatePart(Plate plate) {
            this.plate = plate;
            pView = new PlateView(plate);
            setOpaque(false);

            //the main plate
            OptionsPanel mainPanel = new OptionsPanel();
            mainPanel.setOpaque(false);
            mainPanel.addDividerWithLabel("Plate");
            JScrollPane jScrollPane = new JScrollPane(pView);
            jScrollPane.setBorder(null);
            jScrollPane.setPreferredSize(new Dimension(1, jScrollPane.getPreferredSize().height+20));
            jScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            mainPanel.addSpanningComponent(jScrollPane);

            Map<Cocktail, Integer> cocktailCount = new HashMap<Cocktail, Integer>();
            for(Reaction r : plate.getReactions()) {
                Cocktail c = r.getCocktail();
                if(c != null) {
                    int count = 1;
                    Integer existing = cocktailCount.get(c);
                    if(existing != null) {
                        count += existing;
                    }
                    cocktailCount.put(c, count);
                }
            }
            if(cocktailCount.size() > 0) {
                JPanel cocktailHolderPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
                cocktailHolderPanel.setOpaque(false);
                for(Map.Entry<Cocktail, Integer> entry : cocktailCount.entrySet()) {
                    Cocktail ct = entry.getKey();
                    int count = entry.getValue();
                    OptionsPanel cockatilPanel = new OptionsPanel();
                    for(Options.Option option : ct.getOptions().getOptions()) {
                        if(option instanceof Options.IntegerOption) {
                            Options.IntegerOption integerOption = (Options.IntegerOption)option;
                            JLabel label = new JLabel(integerOption.getValue() * count + " ul");
                            label.setOpaque(false);
                            cockatilPanel.addComponentWithLabel(integerOption.getLabel(), label, false);
                        }
                    }
                    JLabel countLabel = new JLabel("<html><b>" + (ct.getReactionVolume(ct.getOptions()) * count) + " ul</b></html>");
                    countLabel.setOpaque(false);
                    cockatilPanel.addComponentWithLabel("<html><b>Total volume</b></html>", countLabel, false);
                    cockatilPanel.setBorder(new OptionsPanel.RoundedLineBorder(ct.getName(), false));
                    cocktailHolderPanel.add(cockatilPanel);
                }



                mainPanel.addDividerWithLabel("Cocktails");
                mainPanel.addSpanningComponent(cocktailHolderPanel);

                if(plate.getThermocycle() != null) {
                    JPanel thermocyclePanel = new JPanel(new BorderLayout());
                    thermocyclePanel.setOpaque(false);
                    ThermocycleEditor.ThermocycleViewer tViewer = new ThermocycleEditor.ThermocycleViewer(plate.getThermocycle());
                    thermocyclePanel.add(tViewer, BorderLayout.CENTER);
                    JTextArea notes = new JTextArea();
                    notes.setEditable(false);
                    notes.setText(plate.getThermocycle().getNotes());
                    thermocyclePanel.setBorder(new OptionsPanel.RoundedLineBorder(plate.getThermocycle().getName(), false));
                    mainPanel.addDividerWithLabel("Thermocycle");
                    mainPanel.addSpanningComponent(thermocyclePanel);
                }
            }

            setLayout(new BorderLayout());
            add(mainPanel, BorderLayout.CENTER);
        }


        public String getName() {
            return plate.getName();
        }

        public ExtendedPrintable getExtendedPrintable() {
            return null;
        }

        public boolean hasChanges() {
            return false;
        }

        public Plate getPlate() {
            return plate;
        }
    }
}
