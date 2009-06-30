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
import java.awt.print.PrinterException;
import java.awt.print.Printable;

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
        private Map<Cocktail, Integer>  cocktailCount;

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

            cocktailCount = new HashMap<Cocktail, Integer>();
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
                    OptionsPanel cockatilPanel = getCocktailPanel(entry);
                    cocktailHolderPanel.add(cockatilPanel);
                }



                mainPanel.addDividerWithLabel("Cocktails");
                mainPanel.addSpanningComponent(cocktailHolderPanel);

            }

            if(plate.getThermocycle() != null) {
                JPanel thermocyclePanel = getThermocyclePanel(plate);
                mainPanel.addDividerWithLabel("Thermocycle");
                mainPanel.addSpanningComponent(thermocyclePanel);
            }

            setLayout(new BorderLayout());
            add(mainPanel, BorderLayout.CENTER);
        }

        private JPanel getThermocyclePanel(Plate plate) {
            JPanel thermocyclePanel = new JPanel(new BorderLayout());
            thermocyclePanel.setOpaque(false);
            ThermocycleEditor.ThermocycleViewer tViewer = new ThermocycleEditor.ThermocycleViewer(plate.getThermocycle());
            thermocyclePanel.add(tViewer, BorderLayout.CENTER);
            JTextArea notes = new JTextArea();
            notes.setEditable(false);
            notes.setText(plate.getThermocycle().getNotes());
            notes.setWrapStyleWord(true);
            notes.setLineWrap(true);
            thermocyclePanel.add(notes, BorderLayout.SOUTH);
            //thermocyclePanel.setBorder(new OptionsPanel.RoundedLineBorder(plate.getThermocycle().getName(), false));
            JPanel holderPanel = new JPanel(new FlowLayout());
            holderPanel.setBorder(new OptionsPanel.RoundedLineBorder(plate.getThermocycle().getName(), false));
            holderPanel.setOpaque(false);
            holderPanel.add(thermocyclePanel);
            return holderPanel;
        }

        private OptionsPanel getCocktailPanel(Map.Entry<Cocktail, Integer> entry) {
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
            return cockatilPanel;
        }


        public String getName() {
            return plate.getName();
        }

        public ExtendedPrintable getExtendedPrintable() {

            return new ExtendedPrintable(){
                private double masterScale = 0.75;

                public int print(Graphics2D g, Dimension dimensions, int pageIndex, Options options) throws PrinterException {
                    int page = 1;

                    //everything is too big by default...
                    g.scale(masterScale, masterScale);
                    dimensions = new Dimension((int)(dimensions.width/masterScale), (int)(dimensions.height/masterScale));

                    int availableHeight = dimensions.height;
                    double scaleFactor = dimensions.getWidth()/pView.getPreferredSize().width;
                    double requiredPlateHeight = scaleFactor*pView.getPreferredSize().height;

                    if(pageIndex == page) {
                        g.scale(scaleFactor, scaleFactor);
                        pView.setBounds(0,0,pView.getPreferredSize().width, pView.getPreferredSize().height);
                        pView.print(g);
                        g.scale(1/scaleFactor, 1/scaleFactor);
                    }

                    availableHeight -= requiredPlateHeight+10;

                    int cocktailHeight = 0;
                    int cocktailWidth = 0;
                    int maxRowHeight = 0;
                    if(cocktailCount.size() > 0) {
                        for(Map.Entry<Cocktail, Integer> entry : cocktailCount.entrySet()) {
                            JPanel cocktailPanel = getCocktailPanel(entry);
                            int widthIncriment = cocktailPanel.getPreferredSize().width + 10;
                            int heightIncriment = cocktailPanel.getPreferredSize().height + 10;

                            if(cocktailWidth + widthIncriment < dimensions.width) {
                                cocktailWidth += widthIncriment;
                            }
                            else {
                                if (cocktailHeight + maxRowHeight > availableHeight) {
                                    page++;
                                    cocktailHeight = maxRowHeight;
                                    availableHeight = dimensions.height=cocktailHeight;
                                    cocktailWidth = 0;
                                }
                                else {
                                    cocktailWidth = 0;
                                    cocktailHeight += maxRowHeight;
                                    availableHeight -= maxRowHeight;
                                }
                                maxRowHeight = 0;
                            }

                            maxRowHeight = Math.max(maxRowHeight, heightIncriment);

                            if(pageIndex == page) {
                                cocktailPanel.setBounds(0,0, cocktailPanel.getPreferredSize().width, cocktailPanel.getPreferredSize().height);
                                recursiveDoLayout(cocktailPanel);
                                g.translate(cocktailWidth-widthIncriment, dimensions.height - availableHeight + cocktailHeight);
                                disableDoubleBuffering(cocktailPanel);
                                cocktailPanel.paint(g);
                                enableDoubleBuffering(cocktailPanel);
                                g.translate(-cocktailWidth+widthIncriment, -(dimensions.height - availableHeight + cocktailHeight));
                            }


                        }
                        availableHeight -= maxRowHeight;

                    }

                    if(plate.getThermocycle() != null) {
                        int x = 0;
                        int y = 0;


                        JPanel thermocyclePanel = getThermocyclePanel(plate);
                        thermocyclePanel.setBorder(null);

                        if(dimensions.width-cocktailWidth-10 > thermocyclePanel.getPreferredSize().width) {
                            x = ((dimensions.width+cocktailWidth)-thermocyclePanel.getPreferredSize().width)/2;
                            y = dimensions.height-availableHeight-maxRowHeight;
                        }
                        else {
                            x = (dimensions.width-thermocyclePanel.getPreferredSize().width)/2;
                            y = dimensions.height-availableHeight;
                        }

                        if(thermocyclePanel.getPreferredSize().getHeight()+10 > dimensions.height-y) {
                            page++;
                        }
                        if(page == pageIndex) {
                            thermocyclePanel.setBounds(x, y, thermocyclePanel.getPreferredSize().width, thermocyclePanel.getPreferredSize().height);
                            recursiveDoLayout(thermocyclePanel);
                            g.translate(x, y);
                            thermocyclePanel.print(g);
                            g.translate(-x, -y);
                        }
                    }

                    g.scale(1/masterScale, 1/masterScale);
                    return pageIndex > page ? Printable.NO_SUCH_PAGE : Printable.PAGE_EXISTS;
                }

                public int getPagesRequired(Dimension dimensions, Options options) {
                    int page = 1;
                    int availableHeight = dimensions.height;
                    double scaleFactor = dimensions.getWidth()/pView.getPreferredSize().width;
                    double requiredPlateHeight = scaleFactor*pView.getPreferredSize().height;
                    availableHeight -= requiredPlateHeight+10;

                    if(cocktailCount.size() > 0) {
                        int height = 0;
                        int width = 0;
                        for(Map.Entry<Cocktail, Integer> entry : cocktailCount.entrySet()) {
                            JPanel cocktailPanel = getCocktailPanel(entry);
                            if(width + cocktailPanel.getPreferredSize().width+10 < dimensions.width) {
                                width += cocktailPanel.getPreferredSize().width+10;
                            }
                            else if (height + cocktailPanel.getPreferredSize().height + 10 > availableHeight) {
                                page++;
                                height = cocktailPanel.getPreferredSize().height+10;
                                availableHeight = dimensions.height=height;
                                width = 0;
                            }
                            else {
                                width = 0;
                                height += cocktailPanel.getPreferredSize().height+10;
                                availableHeight -= cocktailPanel.getPreferredSize().height+10;
                            }
                        }
                    }

                    if(plate.getThermocycle() != null) {
                        JPanel thermocyclePanel = getThermocyclePanel(plate);
                        if(thermocyclePanel.getPreferredSize().getHeight()+10 > availableHeight) {
                            page++;
                        }
                    }

                    return page;
                }
            };
        }

        public boolean hasChanges() {
            return false;
        }

        public Plate getPlate() {
            return plate;
        }
    }

    public static void recursiveDoLayout(Component c) {
        c.doLayout();
        if(c instanceof Container) {
            for(Component cc : ((Container)c).getComponents()){
                recursiveDoLayout(cc);
            }
        }
    }

    /**
     * The speed and quality of printing suffers dramatically if
     * any of the containers have double buffering turned on.
     * So this turns if off globally.
     */
    public static void disableDoubleBuffering(Component c) {
        RepaintManager currentManager = RepaintManager.currentManager(c);
        currentManager.setDoubleBufferingEnabled(false);
    }

    /**
     * Re-enables double buffering globally.
     */

    public static void enableDoubleBuffering(Component c) {
        RepaintManager currentManager = RepaintManager.currentManager(c);
        currentManager.setDoubleBufferingEnabled(true);
    }
}
