package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.plugin.ExtendedPrintable;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.OptionsPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.print.PrinterException;
import java.awt.print.Printable;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 2/07/2009 11:24:44 AM
 */
public class MultiPartDocumentViewer extends DocumentViewer {
    protected OptionsPanel panel;

    private MuitiPartDocument doc;
    private boolean isLocal;

    public MultiPartDocumentViewer(MuitiPartDocument doc, boolean isLocal) {
        this.doc = doc;
        this.isLocal = isLocal;
    }

    public boolean isLocal() {
        return isLocal;
    }

    public JComponent getComponent() {
        if(panel == null) {
            panel = new OptionsPanel();
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
                holderPanel.setBorder(new OptionsPanel.RoundedLineBorder(part.getName(), false));
                holderPanel.add(part.getPanel(), BorderLayout.CENTER);
                panel.addSpanningComponent(holderPanel);
            }
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

}
