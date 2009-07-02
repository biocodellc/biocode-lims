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
public abstract class MultiPartDocumentViewerFactory extends DocumentViewerFactory{


    public String getDescription() {
        return "Provides a view of "+getName()+"s.";
    }

    public String getHelp() {
        return null;
    }


    public DocumentViewer createViewer(final AnnotatedPluginDocument[] annotatedDocuments) {
        final MuitiPartDocument doc = (MuitiPartDocument)annotatedDocuments[0].getDocumentOrCrash();

        return new MultiPartDocumentViewer(doc, annotatedDocuments[0].isInLocalRepository());
    }

    public static void recursiveDoLayout(Component c) {
        c.doLayout();
        c.validate();
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
