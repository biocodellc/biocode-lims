package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.utilities.StandardIcons;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.print.PrinterException;
import java.awt.print.Printable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

import org.virion.jam.util.SimpleListener;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 2/07/2009 11:24:44 AM
 */
public class MultiPartDocumentViewer extends DocumentViewer {
    protected OptionsPanel panel;
    private GeneiousAction saveAction;

    private MuitiPartDocument doc;
    private boolean isLocal;

    public MultiPartDocumentViewer(final AnnotatedPluginDocument annotatedDocument, MuitiPartDocument document, boolean isLocal) {
        this.doc = document;
        this.isLocal = isLocal;
        saveAction = new GeneiousAction("Save", "", StandardIcons.save.getIcons()){
            public void actionPerformed(ActionEvent e) {
                final BiocodeService.BlockingDialog dialog = BiocodeService.BlockingDialog.getDialog("Saving Reactions", panel);
                Runnable runnable = new Runnable() {
                    public void run() {
                        Connection connection = BiocodeService.getInstance().getActiveLIMSConnection().getConnection();
                        Savepoint savepoint = null;
                        try {
                            connection.setAutoCommit(false);
                            savepoint = connection.setSavepoint("saveReactions");
                            for(int i=0; i < doc.getNumberOfParts(); i++) {
                                MuitiPartDocument.Part p = doc.getPart(i);
                                dialog.setMessage("Saving "+p.getName());
                                if(p.hasChanges()) {
                                    p.saveChangesToDatabase(dialog, connection);
                                }
                            }
                            connection.commit();
                        }
                        catch(SQLException ex) {
                            if(savepoint != null) {
                                try {
                                    if(!BiocodeService.getInstance().getActiveLIMSConnection().isLocal()) {
                                        connection.rollback(savepoint);
                                    }
                                } catch (SQLException ignored) {} //don't need to catch
                            }
                            Dialogs.showMessageDialog("Error saving your reactions: "+ex.getMessage());
                        } finally {
                            try {
                                connection.setAutoCommit(true);
                            } catch (SQLException e1) {
                                e1.printStackTrace();  //don't need to catch this
                            }
                            annotatedDocument.saveDocument();
                            updateToolbar();
                            dialog.setVisible(false);
                        }
                    }
                };
                new Thread(null, runnable, "biocodeSavingWorkflow").start();
                dialog.setVisible(true);
            }
        };
        saveAction.setProOnly(true);
        updateToolbar();

        for(int i=0; i < doc.getNumberOfParts(); i++) {
            doc.getPart(i).addModifiedStateChangedListener(new SimpleListener(){
                public void objectChanged() {
                    updateToolbar();
                }
            });
        }
    }

    private void updateToolbar() {
        saveAction.setEnabled(doc.hasChanges());
    }

    public boolean isLocal() {
        return isLocal;
    }

    @Override
    public ActionProvider getActionProvider() {
        return new ActionProvider() {
            @Override
            public GeneiousAction getSaveAction() {
                return saveAction;
            }
        };
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
                        if(doc.getNumberOfParts() > 1 && booleanOption != null) {
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
