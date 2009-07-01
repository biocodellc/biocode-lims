package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.components.OptionsPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.print.PrinterException;
import java.awt.print.Printable;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 18/06/2009 7:09:36 PM
 */
public class MultiPartDocumentViewerFactory extends DocumentViewerFactory{
    private Type type;

    public enum Type {
        Workflow,
        Plate
    }

    public MultiPartDocumentViewerFactory(Type type) {
        this.type = type;
    }


    public String getName() {
        return type == Type.Workflow ? "Workflow" : "Plate";
    }

    public String getDescription() {
        return "Provides a view of "+getName()+"s.";
    }

    public String getHelp() {
        return null;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        switch(type) {
            case Workflow:
                return new DocumentSelectionSignature[] {new DocumentSelectionSignature(WorkflowDocument.class,1,1)};
            case Plate:
                return new DocumentSelectionSignature[] {new DocumentSelectionSignature(PlateDocument.class,1,1)};
        }
        return new DocumentSelectionSignature[0];
    }

    public DocumentViewer createViewer(final AnnotatedPluginDocument[] annotatedDocuments) {
        final MuitiPartDocument doc = (MuitiPartDocument)annotatedDocuments[0].getDocumentOrCrash();

        return new DocumentViewer(){
            public JComponent getComponent() {
                OptionsPanel panel = new OptionsPanel();
                panel.setOpaque(true);
                panel.setBackground(Color.white);
                for(int i=0; i < doc.getNumberOfParts(); i++) {
                    MuitiPartDocument.Part part = doc.getPart(i);
                    JPanel holderPanel = new JPanel(new BorderLayout()) {
                        public Dimension getPreferredSize() {
                            return new Dimension(10, super.getPreferredSize().height+20);
                        }
                    };
                    holderPanel.setOpaque(false);
                    //holderPanel.setBackground(Color.white);
                    holderPanel.setBorder(new OptionsPanel.RoundedLineBorder(part.getName(), false));
//                    JScrollPane scroller = new JScrollPane(part);
//                    scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
//                    scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
//                    scroller.setBorder(new EmptyBorder(10,10,10,10));
//                    scroller.setOpaque(false);
//                    scroller.getViewport().setOpaque(false);
                    holderPanel.add(part.getPanel(), BorderLayout.CENTER);
                    panel.addSpanningComponent(holderPanel);
                }
                return panel;
            }

            @Override
            public ExtendedPrintable getExtendedPrintable() {
                return new ExtendedPrintable(){
                    @Override
                    public Options getOptions(boolean isSavingToFile) {
                        Options o = new Options(this.getClass());
                        for(int i=0; i < doc.getNumberOfParts(); i++) {
                            MuitiPartDocument.Part part = doc.getPart(i);
                            Options.BooleanOption booleanOption = null;
                            if(doc.getNumberOfParts() > 1) {
                                booleanOption = o.addBooleanOption("" + i, "Print " + part.getName(), true);
                            }
                            Options childOptions = part.getExtendedPrintable().getOptions(isSavingToFile);
                            if(childOptions != null) {
                                o.addChildOptions(""+i, "", "", childOptions);
                                if(doc.getNumberOfParts() > 1) {
                                    booleanOption.addChildOptionsDependent(childOptions, true, true);
                                }
                            }
                        }
                        return o;
                    }

                    public int print(Graphics2D graphics, Dimension dimensions, int pageIndex, Options options) throws PrinterException {
                        int totalPages = -1;
                        for(int i=0; i < doc.getNumberOfParts(); i++) {
                            if(doc.getNumberOfParts() == 1 || ((Options.BooleanOption)options.getOption("" + i)).getValue()) {
                                ExtendedPrintable partPrintable = doc.getPart(i).getExtendedPrintable();
                                Options childOptions = options.getChildOptions().get(""+i);
                                if(partPrintable != null) {
                                    int pagesRequired = partPrintable.getPagesRequired(dimensions, childOptions);
                                    if(pageIndex <= totalPages + pagesRequired) {
                                        partPrintable.print(graphics, dimensions, pageIndex-totalPages, childOptions);
                                        return Printable.PAGE_EXISTS;
                                    }
                                    totalPages += pagesRequired;
                                }
                            }
                        }
                        return Printable.NO_SUCH_PAGE;
                    }

                    public int getPagesRequired(Dimension dimensions, Options options) {
                        int pages = 0;
                        for(int i=0; i < doc.getNumberOfParts(); i++) {
                            if(doc.getNumberOfParts() == 1 || (Boolean)options.getValue(""+i)) {
                                Options childOptions = options.getChildOptions().get(""+i);
                                ExtendedPrintable partPrintable = doc.getPart(i).getExtendedPrintable();
                                if(partPrintable != null) {
                                    pages += partPrintable.getPagesRequired(dimensions, childOptions);
                                }
                            }
                        }
                        return pages;
                    }
                };
            }
        };
    }
}
