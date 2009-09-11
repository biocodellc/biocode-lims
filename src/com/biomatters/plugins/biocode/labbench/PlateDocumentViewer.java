package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.StandardIcons;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.plates.GelEditor;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.PlateView;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.math.MachineAccuracy;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.text.DecimalFormat;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 2/07/2009 11:30:04 AM
 */
public class PlateDocumentViewer extends DocumentViewer{
    private PlateDocument plateDoc;

    private GeneiousAction saveAction;
    private GeneiousAction thermocycleAction;
    private List<Thermocycle> cycles;
    List<SimpleListener> actionsChangedListeners;
    private AnnotatedPluginDocument annotatedDocument;
    private PlateView plateView;
    private Map<Cocktail, Integer> cocktailCount;
    private final boolean isLocal;
    private JComponent container;

    public PlateDocumentViewer(PlateDocument doc, AnnotatedPluginDocument aDoc, boolean local) {
        this.annotatedDocument = aDoc;
        actionsChangedListeners = new ArrayList<SimpleListener>();
        this.plateDoc = doc;
        this.isLocal = local;
        Plate plate = null;
        deletePlateAction.setProOnly(true);
        try {
            plate = new Plate(plateDoc.getPlate().toXML());
        } catch (XMLSerializationException e) {
            throw new RuntimeException("Could not serialise the plate!",e);
        }
        this.plateView = new PlateView(plate, false);
        saveAction = new GeneiousAction("Save") {
            public void actionPerformed(ActionEvent e) {
                if(!isLocal && !BiocodeService.getInstance().isLoggedIn()) {
                    Dialogs.showMessageDialog("Please log in");
                    return;
                }
                plateDoc.setPlate(plateView.getPlate());
                annotatedDocument.saveDocument();
                setEnabled(false);
                if(!isLocal) {
                    final BiocodeService.BlockingDialog dialog = BiocodeService.BlockingDialog.getDialog("Saving your plate", container);
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                if(plateView.getPlate().getReactionType() == Reaction.Type.Extraction) {
                                    BiocodeService.getInstance().saveExtractions(dialog, plateView.getPlate());
                                }
                                else {
                                    BiocodeService.getInstance().saveReactions(dialog, plateView.getPlate());
                                }
                            } catch (SQLException e1) {
                                Dialogs.showMessageDialog("There was an error saving your plate:\n\n"+e1.getMessage());    
                            } catch(BadDataException e2) {
                                Dialogs.showMessageDialog("You have some errors in your plate:\n\n"+e2.getMessage());
                            } finally {
                                dialog.setVisible(false);
                            }
                        }
                    };
                    new Thread(runnable).start();
                    dialog.setVisible(true);
                }
            }
        };
        saveAction.setIcons(StandardIcons.save.getIcons());

        plateView.addSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent e) {
                updateToolbar(false);
            }
        });

        cocktailCount = new HashMap<Cocktail, Integer>();
        



        saveAction.setEnabled(false);
        //bulkEditAction.setProOnly(true);
        editThermocycleAction.setProOnly(true);
        gelAction.setProOnly(true);
        updateToolbar(false);
    }

    public JComponent getComponent() {
        container = new JPanel(new BorderLayout());
        container.setOpaque(true);
        container.setBackground(Color.white);
        container.add(getPanel(), BorderLayout.CENTER);
        return container;
    }

    private void updateCocktailCount() {
        for(Reaction r : plateView.getPlate().getReactions()) {
            if(r.isEmpty()) {
                continue;
            }
            Cocktail c = r.getCocktail();
            if(c != null && c.getId() > 0) {
                int count = 1;
                Integer existing = cocktailCount.get(c);
                if(existing != null) {
                    count += existing;
                }
                cocktailCount.put(c, count);
            }
        }
    }

    private void updateToolbar(boolean showDialogs) {
        updateThermocycleAction(showDialogs);
        boolean buttonsEnabled = !isLocal;
        if(plateView.getPlate().getReactionType() != Reaction.Type.Extraction) {
            thermocycleAction.setEnabled(buttonsEnabled);
            editThermocycleAction.setEnabled(buttonsEnabled);
            gelAction.setEnabled(buttonsEnabled);
        }
        editAction.setEnabled(plateView.getSelectedReactions().size() > 0 && !plateView.getPlate().isDeleted());
        //bulkEditAction.setEnabled(buttonsEnabled);

        if(plateView.getPlate().isDeleted()) {
            for(Action a : getActionProvider().getOtherActions()) {
                a.setEnabled(false);
            }
            saveAction.setEnabled(false);
        }
    }

    private JScrollPane getScrollPane(Container component) {
        for(Component comp : component.getComponents()) {
            if(comp instanceof JScrollPane) {
                return (JScrollPane)comp;
            }
            else if(comp instanceof Container) {
                JScrollPane scroller = getScrollPane((Container)comp);
                if(scroller != null) {
                    return scroller;
                }
            }
        }
        return null;
    }

    private void updatePanel() {
        container.invalidate();
        MultiPartDocumentViewerFactory.recursiveDoLayout(container);
        container.invalidate();
        container.validate();
    }

    private void updateThermocycleAction(boolean showDialogs){
        if(plateView.getPlate().getReactionType() == Reaction.Type.Extraction) {
            return;
        }
        List<GeneiousAction> actions = new ArrayList<GeneiousAction>();
        final AtomicReference<String> name = new AtomicReference<String>(plateView.getPlate().getThermocycle().getName());
        if(!BiocodeService.getInstance().isLoggedIn()) {
            if(showDialogs) {
                Dialogs.showMessageDialog("Please log in");
            }
        }
        else {
            switch(plateView.getPlate().getReactionType()) {
                case Extraction:
                    cycles = Collections.EMPTY_LIST;
                case PCR:
                    cycles = BiocodeService.getInstance().getPCRThermocycles();
                    break;
                case CycleSequencing:
                    cycles = BiocodeService.getInstance().getCycleSequencingThermocycles();
                    break;
            }
            if(cycles.size() == 0) {
                thermocycleAction.setEnabled(false);
                return;
            }
            for(final Thermocycle tc : cycles) {
                GeneiousAction action = new GeneiousAction(tc.getName()) {
                    public void actionPerformed(ActionEvent e) {
                        if(tc.getId() != plateView.getPlate().getThermocycle().getId()) {
                            plateView.getPlate().setThermocycle(tc);
                            updateToolbar(true);
                            saveAction.setEnabled(true);
                            updatePanel();
                        }
                    }
                };
                actions.add(action);
                if(plateView.getPlate().getThermocycle() != null && tc.getId() == plateView.getPlate().getThermocycle().getId()) {
                    name.set(tc.getName());
                }
            }
        }
        thermocycleAction = new GeneiousAction.SubMenu(new GeneiousActionOptions("Thermocycle: "+name.get(), null, BiocodePlugin.getIcons("thermocycle_16.png")), actions);
        thermocycleAction.setProOnly(true);
        Runnable runnable = new Runnable() {
            public void run() {
                for(SimpleListener listener : actionsChangedListeners) {
                    listener.objectChanged();
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    @Override
    public ActionProvider getActionProvider() {
        return new ActionProvider(){
            @Override
            public GeneiousAction getSaveAction() {
                return saveAction;
            }

            @Override
            public List<GeneiousAction> getOtherActions() {
                List<GeneiousAction> actions = new ArrayList<GeneiousAction>();
                if(plateView.getPlate().getReactionType() == Reaction.Type.Extraction) {
                    thermocycleAction = editThermocycleAction = gelAction = null;
                    actions.add(editAction);
                }
                else {
                    actions.addAll(Arrays.asList(
                            thermocycleAction,
                            editThermocycleAction,
                            gelAction,
                            editAction,
                            exportPlateAction
                    ));
                }

                if(!isLocal) {
                    actions.add(deletePlateAction);
                }

                return actions;
            }

            @Override
            public void addActionsChangedListener(SimpleListener listener) {
                actionsChangedListeners.add(listener);
            }

            @Override
            public void removeActionsChangedListener(SimpleListener listener) {
                actionsChangedListeners.remove(listener);
            }
        };
    }

    GeneiousAction gelAction = new GeneiousAction("Attach GEL image", null, BiocodePlugin.getIcons("addImage_16.png")) {
        public void actionPerformed(ActionEvent e) {
            if(!BiocodeService.getInstance().isLoggedIn()) {
                Dialogs.showMessageDialog("Please log in");
                return;
            }
            List<GelImage> gelimages = GelEditor.editGels(plateView.getPlate().getImages(), container);
            plateView.getPlate().setImages(gelimages);
            saveAction.setEnabled(true);
            updatePanel();
        }
    };


    GeneiousAction editThermocycleAction = new GeneiousAction("View/Edit Thermocycles", null, StandardIcons.edit.getIcons()){
        public void actionPerformed(ActionEvent e) {
            if(!BiocodeService.getInstance().isLoggedIn()) {
                Dialogs.showMessageDialog("Please log in");
                return;
            }
            final List<Thermocycle> newThermocycles = ThermocycleEditor.editThermocycles(cycles, container);
            if (newThermocycles.size() > 0) {
                Runnable runnable = new Runnable() {
                    public void run() {
                        BiocodeService.block("Saving Thermocycles", container);
                        try {
                            switch (plateView.getPlate().getReactionType()) {
                                case PCR:
                                    BiocodeService.getInstance().addPCRThermoCycles(newThermocycles);
                                    break;
                                case CycleSequencing:
                                    BiocodeService.getInstance().addCycleSequencingThermoCycles(newThermocycles);
                                    break;
                                default:
                                    assert false : "Extractions do not have thermocycles!";
                                    break;
                            }
                        } catch (final TransactionException e1) {
                            e1.printStackTrace();
                            Runnable runnable = new Runnable() {
                                public void run() {
                                    Dialogs.showMessageDialog("Could not save thermocycles to the database: " + e1.getMessage());
                                }
                            };
                            ThreadUtilities.invokeNowOrLater(runnable);
                        }
                        BiocodeService.unBlock();
                        updateToolbar(false);
                    }
                };
                new Thread(runnable).start();
            }
        }
    };

    GeneiousAction editAction = new GeneiousAction("Edit selected wells", null, StandardIcons.edit.getIcons()) {
        public void actionPerformed(ActionEvent e) {
            ReactionUtilities.editReactions(plateView.getSelectedReactions(), isLocal, plateView, false, false);
            saveAction.setEnabled(true);
            updatePanel();
        }
    };

//    GeneiousAction bulkEditAction = new GeneiousAction("Bulk Edit wells", null, BiocodePlugin.getIcons("bulkEdit_16.png")) {
//        public void actionPerformed(ActionEvent e) {
//            PlateBulkEditor.editPlate(plateView.getPlate(), container);
//            saveAction.setEnabled(true);
//            String error = plateView.getPlate().getReactions()[0].areReactionsValid(Arrays.asList(plateView.getPlate().getReactions()));
//            if(error != null && error.length() > 0) {
//                Dialogs.showMessageDialog(error);
//            }
//            updatePanel();
//        }
//    };

    GeneiousAction exportPlateAction = new GeneiousAction("Generate ABI sequencer file") {
        public void actionPerformed(ActionEvent e) {
            ReactionUtilities.saveAbiFileFromPlate(plateView.getPlate(), plateView);
        }
    };

    GeneiousAction deletePlateAction = new GeneiousAction("Delete Plate", "Removes the plate, and all associated reactions from the database", StandardIcons.delete.getIcons()) {
        public void actionPerformed(ActionEvent e) {
            String message;
            boolean isExtraction = plateView.getPlate().getReactionType() == Reaction.Type.Extraction;
            if(isExtraction) {
                message = "<html><b>WARNING: </b>Deleting an extraction plate will also remove all workflows, PCR, and Cycle Sequencing reactions associated with these extractions.";
            }
            else {
                message = "<html>This will delete all reactions associated with this plate.";
            }

            if(Dialogs.showYesNoDialog(message+"<br>Are you sure you want to continue?</html>", "Delete Plate", plateView, isExtraction ? Dialogs.DialogIcon.WARNING : Dialogs.DialogIcon.INFORMATION)) {
                try {
                    BiocodeService.getInstance().deletePlate(BiocodeService.BlockingDialog.getDialog("Deleting your plate", plateView), plateView.getPlate());
                } catch (SQLException e1) {
                    Dialogs.showMessageDialog("There was an error deleting your plate: "+e1.getMessage());
                    e1.printStackTrace();
                }
                plateView.repaint();
                if(!isLocal && !BiocodeService.getInstance().isLoggedIn()) {
                    Dialogs.showMessageDialog("Please log in");
                    return;
                }
                annotatedDocument.saveDocument();
                updateToolbar(false);
            }


        }
    };



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

    private Map<Cocktail, OptionsPanel> panelMap = new HashMap<Cocktail, OptionsPanel>();

    private OptionsPanel getCocktailPanel(Map.Entry<Cocktail, Integer> entry) {
        final Cocktail ct = entry.getKey();
        final int count = entry.getValue();
        if(panelMap.get(ct) != null) {
            return panelMap.get(ct);
        }
        final JSpinner fudgeSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 100, 1));
        JPanel fudgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fudgePanel.setOpaque(false);
        fudgePanel.add(new JLabel("Fudge factor: "));
        fudgePanel.add(fudgeSpinner);
        fudgePanel.add(new JLabel("%"));
        OptionsPanel cockatilPanel = new OptionsPanel();
        cockatilPanel.addSpanningComponent(fudgePanel);
        String extraItem = ct.getOptions().getValueAsString("extraItem");
        for(final Options.Option option : ct.getOptions().getOptions()) {
            if(option instanceof Options.DoubleOption) {
                int concIndex = option.getName().toLowerCase().indexOf("conc");
                if(concIndex >= 0 && ct.getOptions().getOption(option.getName().substring(0, concIndex)) != null) {
                    continue;
                }
                final Options.DoubleOption doubleOption = (Options.DoubleOption)option;

                String optionLabel = doubleOption.getName();
                if(option.getName().equals("extraItemAmount")) {
                    if(extraItem == null || extraItem.length() == 0) {
                        continue;
                    }
                    optionLabel = extraItem;
                }
                Options.Option concOption = ct.getOptions().getOption(option.getName()+"Conc");
                if(concOption != null && concOption instanceof Options.DoubleOption){
                    optionLabel = concOption.getValue().toString() + " " + ((Options.DoubleOption)concOption).getUnits() + " " + optionLabel;
                }

                final JLabel label = new JLabel();
                ChangeListener listener = new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        double fudgeFactor = 1 + (((Integer) fudgeSpinner.getValue()) / 100.0);
                        if(option.getName().toLowerCase().contains("conc")) { //just display the concentration, it doesn't add (or need a fudge factor)
                            label.setText(doubleOption.getValue() + " " + doubleOption.getUnits());
                        }
                        else {
                            double totalVol = fudgeFactor * doubleOption.getValue() * count;
                            DecimalFormat format = new DecimalFormat("#0.0"+ " " + doubleOption.getUnits());
                            label.setText(format.format(totalVol));
                        }
                    }
                };
                fudgeSpinner.addChangeListener(listener);
                listener.stateChanged(null);
                label.setOpaque(false);
                cockatilPanel.addComponentWithLabel(optionLabel, label, false);
            }
        }
        final JLabel countLabel = new JLabel();
        ChangeListener changeListener = new ChangeListener(){
            public void stateChanged(ChangeEvent e) {
                double fudgeFactor = 1 + (((Integer) fudgeSpinner.getValue()) / 100.0);
                DecimalFormat format = new DecimalFormat("#0.0 uL");
                double totalVol = fudgeFactor * ct.getReactionVolume(ct.getOptions()) * count;
                countLabel.setText("<html><b>" + format.format(totalVol) + "</b></html>");
            }
        };
        fudgeSpinner.addChangeListener(changeListener);
        changeListener.stateChanged(null);
        countLabel.setOpaque(false);
        cockatilPanel.addComponentWithLabel("<html><b>Total volume</b></html>", countLabel, false);
        cockatilPanel.setBorder(new OptionsPanel.RoundedLineBorder(ct.getName(), false));
        panelMap.put(ct, cockatilPanel);
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
        notes.setEditable(false);
        holderPanel.add(notes, BorderLayout.SOUTH);
        return holderPanel;
    }

    private JPanel getImagePanel(final Image img) {
        return new JPanel(){
            @Override
            public Dimension getPreferredSize() {
                if (isPreferredSizeSet()) {
                    return super.getPreferredSize();
                }
                return new Dimension(img.getWidth(this), img.getHeight(this));
            }

            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0,0,getWidth(),getHeight());
                double scaleFactor = Double.MAX_VALUE;

                scaleFactor = Math.min(scaleFactor, (double)getWidth()/img.getWidth(this));
                scaleFactor = Math.min(scaleFactor, (double)getHeight()/img.getHeight(this));
                ((Graphics2D)g).scale(scaleFactor, scaleFactor);
                g.drawImage(img, 0, 0, this);
                ((Graphics2D)g).scale(1/scaleFactor, 1/scaleFactor);
            }
        };
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
                if(plateView.getPlate().getImages() != null && plateView.getPlate().getImages().size() > 0) {
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
                boolean oldColorBackground = plateView.isColorBackground();
                plateView.setColorBackground((Boolean)options.getValue("colorPlate"));
                double scaleFactor = dimensions.getWidth()/plateView.getPreferredSize().width;
                scaleFactor = Math.min(scaleFactor, dimensions.getHeight()/plateView.getPreferredSize().height);
                double requiredPlateHeight = scaleFactor*plateView.getPreferredSize().height;

                if(pageIndex+1 == page && g != null) {
                    g.scale(scaleFactor, scaleFactor);
                    plateView.setBounds(0,0,plateView.getPreferredSize().width, plateView.getPreferredSize().height);
                    plateView.print(g);
                    g.scale(1/scaleFactor, 1/scaleFactor);
                }
                plateView.setColorBackground(oldColorBackground);

                availableHeight -= requiredPlateHeight+10;

                int cocktailHeight = 0;
                int cocktailWidth = 0;
                int maxRowHeight = 0;
                if(cocktailCount.size() > 0) {
                    for(Map.Entry<Cocktail, Integer> entry : cocktailCount.entrySet()) {
                        JPanel cocktailPanel = getCocktailPanel(entry);
                        ((Container)((JScrollPane)cocktailPanel.getComponent(0)).getViewport().getView()).getComponent(0).setVisible(false);
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

                        if(pageIndex+1 == page && g != null) {
                            cocktailPanel.setBounds(0,0, cocktailPanel.getPreferredSize().width, cocktailPanel.getPreferredSize().height);
                            MultiPartDocumentViewerFactory.recursiveDoLayout(cocktailPanel);
                            cocktailPanel.validate();
                            g.translate(cocktailWidth-widthIncriment, dimensions.height - availableHeight + cocktailHeight);
                            cocktailPanel.print(g);
                            g.translate(-cocktailWidth+widthIncriment, -(dimensions.height - availableHeight + cocktailHeight));
                        }
                        ((Container)((JScrollPane)cocktailPanel.getComponent(0)).getViewport().getView()).getComponent(0).setVisible(true);


                    }
                    availableHeight -= maxRowHeight;

                }

                if(plateView.getPlate().getThermocycle() != null) {
                    int x = 0;
                    int y = 0;


                    JPanel thermocyclePanel = getThermocyclePanel(plateView.getPlate());
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
                    if(page == pageIndex+1 && g != null) {
                        thermocyclePanel.setBounds(x, y, thermocyclePanel.getPreferredSize().width, thermocyclePanel.getPreferredSize().height+30);
                        MultiPartDocumentViewerFactory.recursiveDoLayout(thermocyclePanel);
                        thermocyclePanel.validate();
                        thermocyclePanel.invalidate();
                        g.translate(x, y);
                        thermocyclePanel.print(g);
                        g.translate(-x, -y);
                    }
                }

                if(plateView.getPlate().getImages() != null && plateView.getPlate().getImages().size() > 0 && (Boolean)options.getValue("printImages")) {
                    for(GelImage image : plateView.getPlate().getImages()) {
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
                        if(pageIndex+1 == page && g != null) {
                            MultiPartDocumentViewerFactory.recursiveDoLayout(imagePanel);
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

    public JPanel getPanel() {
        JPanel panel = new JPanel();

        panel.setOpaque(false);

        //the main plate
        OptionsPanel mainPanel = new OptionsPanel(true, false);
        mainPanel.setOpaque(false);
        mainPanel.addDividerWithLabel("Plate");
        JScrollPane jScrollPane = new JScrollPane(plateView);
        jScrollPane.setBorder(null);
        jScrollPane.setPreferredSize(new Dimension(1, jScrollPane.getPreferredSize().height+20));
        jScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        mainPanel.addSpanningComponent(jScrollPane);

        updateCocktailCount();

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

        if(plateView.getPlate().getThermocycle() != null) {
            JPanel thermocyclePanel = getThermocyclePanel(plateView.getPlate());
            mainPanel.addDividerWithLabel("Thermocycle");
            mainPanel.addSpanningComponent(thermocyclePanel);
        }

        if(plateView.getPlate().getImages() != null && plateView.getPlate().getImages().size() > 0) {
            mainPanel.addDividerWithLabel("GEL Images");
            for(final GelImage image : plateView.getPlate().getImages()) {
                final JPanel imagePanel = getGelImagePanel(image);
                imagePanel.setToolTipText("Double-click to pop out");
                imagePanel.addMouseListener(new MouseAdapter(){
                    JFrame frame;
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if(e.getClickCount() == 2) {
                            final JPanel gelImagePanel = getImagePanel(image.getImage());
                            final Dimension originalImageSize = new Dimension(gelImagePanel.getPreferredSize());
                            if(frame == null || !frame.isShowing()) {
                                frame = new JFrame();
                                frame.setTitle("GEL Image");
                                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                                frame.setSize(640, 480);
                                frame.setLocationRelativeTo(imagePanel);
                                final AtomicReference<Double> zoom = new AtomicReference<Double>(1.0);
                                final ZoomPanel zoomer = new ZoomPanel();
                                zoomer.setZoomLevel(zoom.get());
                                zoomer.setMaximumZoomLevel(4.0);
                                frame.getContentPane().add(zoomer, BorderLayout.NORTH);
                                JPanel holder = new JPanel(new FlowLayout());
                                holder.add(gelImagePanel);
                                final JScrollPane scroller = new JScrollPane(holder);
                                frame.getContentPane().add(scroller, BorderLayout.CENTER);

                                frame.addComponentListener(new ComponentAdapter(){
                                    @Override
                                    public void componentResized(ComponentEvent e) {
                                        boolean isAtMinZoom = MachineAccuracy.same(zoomer.getZoomLevel(),zoomer.getMinZoomLevel());
                                        double newMinZoom = Math.min(1.0, Math.min(scroller.getViewport().getSize().getWidth()/originalImageSize.getWidth(), scroller.getViewport().getSize().getHeight()/originalImageSize.getHeight()));
                                        zoomer.setMinimumAndFitToScreenZoomLevel(newMinZoom);
                                        if(isAtMinZoom) {
                                            zoomer.setZoomLevel(newMinZoom);
                                        }
                                    }
                                });

                                zoomer.addChangeListener(new ChangeListener(){
                                    public void stateChanged(ChangeEvent e) {
                                        Dimension preferredSize = new Dimension((int) (originalImageSize.width * zoomer.getZoomLevel()), (int) (originalImageSize.height * zoomer.getZoomLevel()));
                                        gelImagePanel.setPreferredSize(preferredSize);
                                        gelImagePanel.invalidate();
                                        scroller.invalidate();
                                        scroller.validate();
                                    }
                                });

                                frame.setVisible(true);
                            }
                            else {
                                frame.requestFocus();
                            }
                        }
                    }
                });
                mainPanel.addSpanningComponent(imagePanel);
            }
        }

        panel.setLayout(new BorderLayout());
        panel.add(mainPanel, BorderLayout.CENTER);

        return panel;
    }


}