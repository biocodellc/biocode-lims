package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.plugins.moorea.plates.Plate;
import com.biomatters.plugins.moorea.plates.PlateView;
import com.biomatters.plugins.moorea.plates.GelImage;
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
        private Map<Cocktail, Integer>  cocktailCount;

        public PlatePart(Plate plate) {
            this.plate = plate;


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

        }

        public JPanel getPanel() {
            JPanel panel = new JPanel();

            PlateView pView = new PlateView(plate);
            panel.setOpaque(false);

            //the main plate
            OptionsPanel mainPanel = new OptionsPanel();
            mainPanel.setOpaque(false);
            mainPanel.addDividerWithLabel("Plate");
            JScrollPane jScrollPane = new JScrollPane(pView);
            jScrollPane.setBorder(null);
            jScrollPane.setPreferredSize(new Dimension(1, jScrollPane.getPreferredSize().height+20));
            jScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            mainPanel.addSpanningComponent(jScrollPane);

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

            if(plate.getImages() != null && plate.getImages().size() > 0) {
                mainPanel.addDividerWithLabel("GEL Images");
                for(GelImage image : plate.getImages()) {
                    mainPanel.addSpanningComponent(getGelImagePanel(image));
                }
            }

            panel.setLayout(new BorderLayout());
            panel.add(mainPanel, BorderLayout.CENTER);

            return panel;
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

        private JPanel getGelImagePanel(GelImage gelImage) {
            final Image img = gelImage.getImage();
            JPanel imagePanel = getImagePanel(img);
            JPanel holderPanel = new JPanel(new BorderLayout());
            holderPanel.setOpaque(false);
            holderPanel.add(imagePanel, BorderLayout.CENTER);
            JTextArea notes = new JTextArea(gelImage.getNotes());
            notes.setWrapStyleWord(true);
            notes.setLineWrap(true);
            holderPanel.add(notes, BorderLayout.SOUTH);
            return holderPanel;
        }

        private JPanel getImagePanel(final Image img) {
            return new JPanel(){
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(img.getWidth(this), img.getHeight(this));
                }

                @Override
                protected void paintComponent(Graphics g) {
                    double scaleFactor = 1.0;
                    if(img.getWidth(this) > getWidth() || img.getHeight(this) > getHeight()) {
                        scaleFactor = Math.min(scaleFactor, (double)getWidth()/img.getWidth(this));
                        scaleFactor = Math.min(scaleFactor, (double)getHeight()/img.getHeight(this));
                    }
                    ((Graphics2D)g).scale(scaleFactor, scaleFactor);
                    g.drawImage(img, 0, 0, this);
                    ((Graphics2D)g).scale(1/scaleFactor, 1/scaleFactor);
                }
            };
        }


        public String getName() {
            return plate.getName();
        }

        public ExtendedPrintable getExtendedPrintable() {

            return new ExtendedPrintable(){
                private double masterScale = 0.75;

                public int print(Graphics2D graphics, Dimension dimensions, int pageIndex, Options options) throws PrinterException {
                    int pages = printAndPagesRequired(graphics, dimensions, pageIndex, options);
                    return pages > pageIndex ? Printable.PAGE_EXISTS : Printable.NO_SUCH_PAGE;
                }

                public int getPagesRequired(Dimension dimensions, Options options) {
                    try {
                        return printAndPagesRequired(null, dimensions, -1, options);
                    } catch (PrinterException e) {
                        return 0;
                    }
                }

                @Override
                public Options getOptions(boolean isSavingToFile) {
                    Options o = new Options(this.getClass());
                    o.addBooleanOption("colorPlate", "Color the plate", true);
                    if(plate.getImages() != null && plate.getImages().size() > 0) {
                        o.addBooleanOption("printImages", "Print GEL Images", true);
                    }
                    return o;
                }

                public int printAndPagesRequired(Graphics2D g, Dimension dimensions, int pageIndex, Options options) throws PrinterException {
                    int page = 1;

                    //everything is too big by default...
                    if(g != null) {
                        g.scale(masterScale, masterScale);
                    }
                    dimensions = new Dimension((int)(dimensions.width/masterScale), (int)(dimensions.height/masterScale));

                    int availableHeight = dimensions.height;
                    PlateView pView = new PlateView(plate);
                    pView.setColorBackground((Boolean)options.getValue("colorPlate"));
                    double scaleFactor = dimensions.getWidth()/pView.getPreferredSize().width;
                    scaleFactor = Math.min(scaleFactor, dimensions.getHeight()/pView.getPreferredSize().height);
                    double requiredPlateHeight = scaleFactor*pView.getPreferredSize().height;

                    if(pageIndex == page && g != null) {
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
                                    availableHeight = dimensions.height;
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

                            if(pageIndex == page && g != null) {
                                cocktailPanel.setBounds(0,0, cocktailPanel.getPreferredSize().width, cocktailPanel.getPreferredSize().height);
                                recursiveDoLayout(cocktailPanel);
                                cocktailPanel.validate();
                                g.translate(cocktailWidth-widthIncriment, dimensions.height - availableHeight + cocktailHeight);
                                cocktailPanel.print(g);
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
                        thermocyclePanel.setSize(thermocyclePanel.getPreferredSize());

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
                            availableHeight = dimensions.height;
                            x = (dimensions.width-thermocyclePanel.getPreferredSize().width)/2;
                            y = dimensions.height-availableHeight;
                        }
                        if(page == pageIndex && g != null) {
                            thermocyclePanel.setBounds(x, y, thermocyclePanel.getPreferredSize().width, thermocyclePanel.getPreferredSize().height+30);
                            recursiveDoLayout(thermocyclePanel);
                            thermocyclePanel.validate();
                            thermocyclePanel.invalidate();
                            g.translate(x, y);
                            thermocyclePanel.print(g);
                            g.translate(-x, -y);
                        }
                    }

                    if(plate.getImages() != null && plate.getImages().size() > 0 && (Boolean)options.getValue("printImages")) {
                        for(GelImage image : plate.getImages()) {
                            JPanel imagePanel = getGelImagePanel(image);

                            Dimension preferredSize = imagePanel.getPreferredSize();
                            if(preferredSize.height > availableHeight) {
                                page++;
                                availableHeight = dimensions.height;
                            }
                            if(preferredSize.width > dimensions.width || imagePanel.getPreferredSize().height > availableHeight) {
                                double imageScaleFactor = 1.0;
                                imageScaleFactor = Math.min(imageScaleFactor,(double)dimensions.width/ preferredSize.width);
                                imageScaleFactor = Math.min(imageScaleFactor, (double)dimensions.height/preferredSize.height);
                                imagePanel.setSize(new Dimension((int)(preferredSize.width*imageScaleFactor), (int)(preferredSize.height*imageScaleFactor)));
                            }
                            else {
                                imagePanel.setSize(preferredSize);
                            }
                            if(pageIndex == page && g != null) {
                                recursiveDoLayout(imagePanel);
                                g.translate(0, dimensions.height-availableHeight);
                                imagePanel.print(g);
                                g.translate(0, availableHeight-dimensions.height);
                            }
                            availableHeight-=imagePanel.getHeight();
                        }
                    }

                    if(g != null) {
                        g.scale(1/masterScale, 1/masterScale);
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
