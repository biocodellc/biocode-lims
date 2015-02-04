package com.biomatters.plugins.biocode.labbench.plates;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionUtilities;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.virion.jam.util.SimpleListener;


public class PlateView extends JPanel {

    private PlateView selfReference = this;
    private Plate plate;
    private boolean colorBackground = true;
    private boolean selectAll = false;
    boolean creating = false;

    private int zoom = 9;



    public PlateView(int numberOfWells, Reaction.Type type, boolean creating) {
        this.creating = creating;
        plate = new Plate(numberOfWells, type);
        init();
    }


    public PlateView(Plate.Size size, Reaction.Type type, boolean creating) {
        this.creating = creating;
        plate = new Plate(size, type);
        init();
    }

    public PlateView(Plate plate, boolean creating) {
        this.creating = creating;
        this.plate = plate;
        init();
    }

    public Plate getPlate() {
        return plate;
    }

    public void decreaseZoom () {
        zoom--;
        if(zoom < 5) {
            zoom = 5;
        }
        updateZoom();
    }

    public void increaseZoom () {
        zoom++;
        if(zoom > 15) {
            zoom = 15;
        }
        updateZoom();
    }

    private void updateZoom() {
        for(Reaction r : getPlate().getReactions()) {
            r.setBaseFontSize(zoom);
        }
        repaint();
    }

    public void setDefaultZoom() {
        zoom = 10;
        updateZoom();
    }


    @Override
    protected void paintComponent(Graphics g1) {
        int cols = plate.getCols();
        int rows = plate.getRows();
        Reaction[] reactions = plate.getReactions();

        Graphics2D g = (Graphics2D)g1;
        int cellWidth = (getWidth()+1)/cols;
        int cellHeight = (getHeight())/rows;

        g.setColor(Color.white);
        g.fillRect(0,0,getWidth(),getHeight());

        g.setColor(getBackground());
        g.fillRect(0,0,cellWidth*cols+1,cellHeight*rows-1);
        Shape clip = g.getClip();


        for(int i=0; i < rows; i++) {
            for(int j = 0; j < cols; j++) {
                final Reaction reaction = reactions[cols*i + j];
                Rectangle reactionBounds = new Rectangle(1+cellWidth * j, 1+cellHeight * i, cellWidth - 1, cellHeight - 1);
                reaction.setBounds(reactionBounds);
                g.clip(reactionBounds);
                reaction.paint(g, colorBackground, !plate.isDeleted());
                g.setClip(clip);
            }
        }

        g.setColor(Color.black);
        g.drawRect(0,0,cellWidth*cols+1,cellHeight*rows-1);
        //System.out.println("paintin: "+(System.currentTimeMillis()-time));
    }

    public boolean isColorBackground() {
        return colorBackground;
    }

    public void setColorBackground(boolean colorBackground) {
        this.colorBackground = colorBackground;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = 0;
        int height = 0;

        Reaction[] reactions = plate.getReactions();

        for(int i=0; i < plate.getRows(); i++) {
            for(int j = 0; j < plate.getCols(); j++) {
                height = Math.max(height, reactions[j*i + j].getPreferredSize().height);
                width = Math.max(width, reactions[j*i + j].getPreferredSize().width);
            }
        }


        return new Dimension(1+(width+1)*plate.getCols(), 1+(height+1)*plate.getRows());
    }

    private Point mousePos = new Point(0,0);
    private Boolean[] wasSelected;

    private void init() {

        final Reaction[] reactions = plate.getReactions();
        setBackground(Color.black);

        addMouseListener(new MouseAdapter(){
            @Override
            public void mouseClicked(MouseEvent e) {
                if(plate.isDeleted()) {
                    return;
                }
                requestFocus();
                boolean ctrlIsDown = (e.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) > 0;
                boolean shiftIsDown = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) == MouseEvent.SHIFT_DOWN_MASK;

                if(mousePos != null && shiftIsDown) {
                    if(wasSelected == null) {
                        initWasSelected(reactions);
                    }
                    selectRectangle(e);
                    repaint();
                    return;
                }



                if(e.getClickCount() == 1) {
                    //select just the cell the user clicked on
                    for (Reaction reaction : reactions) {
                        if (reaction.getBounds().contains(e.getPoint())) {
                            reaction.setSelected(ctrlIsDown ? !reaction.isSelected() : true);
                        } else {
                            if (!ctrlIsDown) {
                                reaction.setSelected(false);
                            }
                        }
                    }
                }
                if(e.getClickCount() == 2) {
                    final AtomicBoolean editResult = new AtomicBoolean();
                    final ProgressFrame progressFrame = BiocodeUtilities.getBlockingProgressFrame("Making changes...", selfReference);
                    new Thread() {
                        public void run() {
                            progressFrame.setIndeterminateProgress();
                            final List<Reaction> selectedReactions = getSelectedReactions();
                            if(selectedReactions.size()>0)
                            {
                                editResult.set(ReactionUtilities.editReactions(Arrays.asList(selectedReactions.toArray(new Reaction[selectedReactions.size()])), selfReference, creating));

                            }
                            progressFrame.setComplete();
                            ThreadUtilities.invokeNowOrLater(new Runnable() {
                                @Override
                                public void run() {
                                    if (editResult.get()) {
                                        fireEditListeners();
                                    }
                                    for (Reaction r : selectedReactions) {
                                        r.invalidateFieldWidthCache();
                                    }
                                    repaint();
                                    revalidate();
                                    repaint();
                                }
                            });
                        }
                    }.start();
                }
                fireSelectionListeners();
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if(plate.isDeleted()) {
                    return;
                }
                selectAll = false;
                Reaction[] reactions = plate.getReactions();
                boolean shiftIsDown = (e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK) == MouseEvent.SHIFT_DOWN_MASK;
                if(!shiftIsDown) {
                    mousePos = e.getPoint();
                }

                initWasSelected(reactions);

            }

            @Override
            public void mouseReleased(MouseEvent e) {
                wasSelected = null;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter(){
            @Override
            public void mouseDragged(MouseEvent e) {
                if(plate.isDeleted()) {
                    return;
                }
                selectRectangle(e);
                repaint();
            }
        });

        addKeyListener(new KeyAdapter(){
            @Override
            public void keyPressed(KeyEvent e) {
                if(plate.isDeleted()) {
                    return;
                }
                if(e.getKeyCode() == KeyEvent.VK_A && (e.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) > 0) {
                    selectAll = !selectAll;
                    for(Reaction r : reactions) {
                        r.setSelected(selectAll);
                    }
                    repaint();
                }
                repaint();
            }
        });
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_A, GuiUtilities.MENU_MASK), "select-all");
    }

    private void initWasSelected(Reaction[] reactions) {
        wasSelected = new Boolean[reactions.length];
        for(int i=0; i < reactions.length; i++) {
            wasSelected[i] = reactions[i].isSelected();
        }
    }


    public List<Reaction> getSelectedReactions() {
        List<Reaction> selectedReactions = new ArrayList<Reaction>();
        for(Reaction reaction : plate.getReactions()) {
            if(reaction.isSelected()) {
                selectedReactions.add(reaction);
            }
        }
        return selectedReactions;
    }

    private List<ListSelectionListener> selectionListeners = new ArrayList<ListSelectionListener>();
    private List<SimpleListener> editListeners = new ArrayList<SimpleListener>();

    public void addSelectionListener(ListSelectionListener lsl) {
        selectionListeners.add(lsl);
    }

    /**
     * adds a listener that's fired when one or more of the wells in this plate are edited
     * @param listener
     */
    public void addEditListener(SimpleListener listener) {
        editListeners.add(listener);
    }

    private void fireEditListeners() {
        for(SimpleListener listener : editListeners) {
            listener.objectChanged();
        }
    }

    private void fireSelectionListeners() {
        for(ListSelectionListener listener : selectionListeners) {
            listener.valueChanged(new ListSelectionEvent(this, 0,plate.getReactions().length-1,false));
        }
    }

    private void selectRectangle(MouseEvent e) {
        Rectangle selectionRect = createRect(mousePos, e.getPoint());
        boolean ctrlIsDown = (e.getModifiers() & Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) > 0;
        Reaction[] reactions = plate.getReactions();

        //select all wells within the selection rectangle
        for(int i=0; i < reactions.length; i++) {
            Rectangle bounds = reactions[i].getBounds();
            if(selectionRect.intersects(bounds)) {
                reactions[i].setSelected(true);
            }
            else {
                reactions[i].setSelected(ctrlIsDown && wasSelected != null ? wasSelected[i] : false);
            }
        }
        fireSelectionListeners();
    }

    /**
     * creates a rectangle between the two points
     * @param p1 a point representing one corner of the rectangle
     * @param p2 a point representing the opposite corner of the rectangle
     * @return the finished rectangle
     */
    private Rectangle createRect(Point p1, Point p2) {
        int x,y,w,h;
        if(p1.x < p2.x) {
            x = p1.x;
            w = p2.x-p1.x;
        }
        else {
            x = p2.x;
            w = p1.x-p2.x;
        }

        if(p1.y < p2.y) {
            y = p1.y;
            h = p2.y-p1.y;
        }
        else {
            y = p2.y;
            h = p1.y-p2.y;
        }
        return new Rectangle(x,y,w,h);
    }


    public void setPlate(Plate plate) {
        this.plate = plate;
    }

    public void checkReactionsForErrors(boolean isNewPlate) {
        Reaction[] reactions = getPlate().getReactions();

        if (reactions.length > 0) {
            setReactionErrorStates(Arrays.asList(reactions), false);

            String reactionCheckResult = reactions[0].areReactionsValid(Arrays.asList(reactions), this, true);
            if (reactionCheckResult != null) {
                Dialogs.showMessageDialog(reactionCheckResult);
            }

            try {
                if (isNewPlate && reactions[0].getType().equals(Reaction.Type.Extraction)) {
                    checkExtractionReactionsForErrors(castReactionsToExtractionReactions(Arrays.asList(reactions)));
                }
            } catch (DatabaseServiceException e) {
                Dialogs.showMessageDialog("An error was encountered while attempting to check the plate for extraction reaction specific errors: " + e.getMessage() + ".");
            }
        }
    }

    private static void checkExtractionReactionsForErrors(Collection<ExtractionReaction> extractionReactions) throws DatabaseServiceException {
        checkForExistingExtractionReactionsAssociatedWithExtractionBarcodesOfNewExtractionReactions(extractionReactions);
    }

    private static void checkForExistingExtractionReactionsAssociatedWithExtractionBarcodesOfNewExtractionReactions(Collection<ExtractionReaction> extractionReactions) throws DatabaseServiceException {
        Map<String, List<ExtractionReaction>> extractionBarcodeToNewExtractionReactions = buildBarcodeToExtractionReactionsMap(extractionReactions);
        Map<String, List<ExtractionReaction>> extractionBarcodeToExistingExtractionReactions = buildBarcodeToExtractionReactionsThatHaveNotJustBeenMovedMap(
                BiocodeService.getInstance().getActiveLIMSConnection().getExtractionsFromBarcodes(new ArrayList<String>(extractionBarcodeToNewExtractionReactions.keySet()))
        );
        Map<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionReactionsToNewExtractionReactions = buildExistingExtractionsReactionsToNewExtractionReactionsMap(
                extractionBarcodeToNewExtractionReactions,
                extractionBarcodeToExistingExtractionReactions
        );

        if (!extractionBarcodeToExistingExtractionReactions.isEmpty()) {
            if (Dialogs.showYesNoDialog(
                    "Extraction reactions that are associated with the following extraction barcodes already exist: " + StringUtilities.join(", ", extractionBarcodeToExistingExtractionReactions.keySet()) +
                            ".<br><br> Move the existing extractions to the plate and override the corresponding new extractions?",
                    "Existing Extractions With Barcodes Detected",
                    null,
                    Dialogs.DialogIcon.QUESTION)) {
                overrideNewExtractionReactionsWithExistingReactionsThatAreAssociatedWithTheSameBarcode(existingExtractionReactionsToNewExtractionReactions);
            } else {
                List<Reaction> newExtractionReactionsAssociatedWithAnExistingBarcode = new ArrayList<Reaction>();

                for (List<ExtractionReaction> groupOfNewExtractionReactionsAssociatedWithSameExistingBarcode : existingExtractionReactionsToNewExtractionReactions.values()) {
                    newExtractionReactionsAssociatedWithAnExistingBarcode.addAll(groupOfNewExtractionReactionsAssociatedWithSameExistingBarcode);
                }

                setReactionErrorStates(newExtractionReactionsAssociatedWithAnExistingBarcode, true);
            }
        }
    }

    private static Map<List<ExtractionReaction>, List<ExtractionReaction>> buildExistingExtractionsReactionsToNewExtractionReactionsMap(Map<String, List<ExtractionReaction>> extractionBarcodeToNewExtractionReactions,
                                                                                                                                        Map<String, List<ExtractionReaction>> extractionBarcodeToExistingExtractionReactions) {
        Map<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionsToNewExtractions = new HashMap<List<ExtractionReaction>, List<ExtractionReaction>>();

        for (Map.Entry<String, List<ExtractionReaction>> extractionBarcodeAndExtractionsThatExist : extractionBarcodeToExistingExtractionReactions.entrySet()) {
            existingExtractionsToNewExtractions.put(extractionBarcodeAndExtractionsThatExist.getValue(), extractionBarcodeToNewExtractionReactions.get(extractionBarcodeAndExtractionsThatExist.getKey()));
        }

        return existingExtractionsToNewExtractions;
    }

    private static Map<String, List<ExtractionReaction>> buildBarcodeToExtractionReactionsThatHaveNotJustBeenMovedMap(Collection<ExtractionReaction> extractionReactions) throws DatabaseServiceException {
        Collection<ExtractionReaction> extractionReactionsThatExistAndHaveNotJustBeenMoved = new ArrayList<ExtractionReaction>();

        for (ExtractionReaction extractionReaction : extractionReactions) {
            if (extractionReaction.isJustMoved()) {
                extractionReaction.setJustMoved(false);
            } else {
                extractionReactionsThatExistAndHaveNotJustBeenMoved.add(extractionReaction);
            }
        }

        return buildBarcodeToExtractionReactionsMap(extractionReactionsThatExistAndHaveNotJustBeenMoved);
    }

    private static Map<String, List<ExtractionReaction>> buildBarcodeToExtractionReactionsMap(Collection<ExtractionReaction> extractionReactions) {
        Map<String, List<ExtractionReaction>> barcodeToExtractionReactions = new HashMap<String, List<ExtractionReaction>>();

        for (ExtractionReaction extractionReaction : extractionReactions) {
            String extractionBarcode = extractionReaction.getOptions().getValueAsString(BiocodeUtilities.EXTRACTION_BARCODE_FIELD.getCode());

            if (extractionBarcode != null && !extractionBarcode.isEmpty()) {
                List<ExtractionReaction> extractionReactionsAssociatedWithBarcode = barcodeToExtractionReactions.get(extractionBarcode);

                if (extractionReactionsAssociatedWithBarcode == null) {
                    extractionReactionsAssociatedWithBarcode = new ArrayList<ExtractionReaction>();

                    barcodeToExtractionReactions.put(extractionBarcode, extractionReactionsAssociatedWithBarcode);
                }

                extractionReactionsAssociatedWithBarcode.add(extractionReaction);
            }
        }

        return barcodeToExtractionReactions;
    }

    private static void overrideNewExtractionReactionsWithExistingReactionsThatAreAssociatedWithTheSameBarcode(Map<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionReactionsToNewExtractionReactions) {
        List<String> extractionReactionsThatCouldNotBeOverridden = new ArrayList<String>();

        for (Map.Entry<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionReactionsAndNewExtractionReactions : existingExtractionReactionsToNewExtractionReactions.entrySet()) {
            List<ExtractionReaction> newExtractionReactions = existingExtractionReactionsAndNewExtractionReactions.getValue();

            if (newExtractionReactions.size() > 1) {
                setReactionErrorStates(newExtractionReactions, true);

                extractionReactionsThatCouldNotBeOverridden.add("Extraction barcode: " + newExtractionReactions.get(0).getExtractionBarcode() + ".\n" + "Well numbers: " + StringUtilities.join(", ", getReactionLocations(newExtractionReactions)) + ".");
            } else {
                ExtractionReaction.copyExtractionReaction(getExistingExtractionReactionToMove(existingExtractionReactionsAndNewExtractionReactions.getKey()), newExtractionReactions.get(0));
            }
        }

        if (!extractionReactionsThatCouldNotBeOverridden.isEmpty()) {
            Dialogs.showMessageDialog(
                    "Cannot multiple new reactions that are associated with the same existing barcode:<br><br>" + StringUtilities.join("\n\n", extractionReactionsThatCouldNotBeOverridden),
                    "Multiple Extractions Associated With Same Existing Barcode",
                    null,
                    Dialogs.DialogIcon.WARNING
            );
        }
    }

    private static ExtractionReaction getExistingExtractionReactionToMove(Collection<ExtractionReaction> existingExtractionReactions) {
        // todo: determine how to handle when existingExtractionReactions.size() > 1;
        return existingExtractionReactions.iterator().next();
    }

    private static void setReactionErrorStates(Collection<? extends Reaction> reactions, boolean errorState) {
        for (Reaction reaction : reactions) {
            reaction.setHasError(errorState);
        }
    }

    private static Collection<ExtractionReaction> castReactionsToExtractionReactions(Collection<Reaction> reactions) {
        Collection<ExtractionReaction> result = new ArrayList<ExtractionReaction>();

        for (Reaction reaction : reactions) {
            result.add((ExtractionReaction)reaction);
        }

        return result;
    }

    private static Collection<String> getReactionLocations(Collection<? extends Reaction> reactions) {
        Collection<String> reactionLocations = new ArrayList<String>();

        for (Reaction reaction : reactions) {
            reactionLocations.add(reaction.getLocationString());
        }

        return reactionLocations;
    }
}