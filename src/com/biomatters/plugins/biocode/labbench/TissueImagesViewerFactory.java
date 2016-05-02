package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.net.URL;
import java.io.IOException;
import java.util.List;

import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;

/**
 * @author Steven Stones-Havas
 *          <p/>
 *          Created on 3/06/2009 1:11:31 PM
 */
public class TissueImagesViewerFactory extends DocumentViewerFactory{
    public String getName() {
        return "Photos";
    }

    public String getDescription() {
        return "Available photos of the specimen";
    }

    public String getHelp() {
        return null;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {new DocumentSelectionSignature(TissueDocument.class, 1, 1)};
    }

    public DocumentViewer createViewer(final AnnotatedPluginDocument[] annotatedDocuments) {
        final FIMSConnection activeFIMSConnection = BiocodeService.getInstance().getActiveFIMSConnection();
        if(activeFIMSConnection == null || !activeFIMSConnection.hasPhotos()) {
            return null;
        }
        return new DocumentViewer(){
            JPanel panel;
            public JComponent getComponent() {
                final TissueDocument doc = (TissueDocument)annotatedDocuments[0].getDocumentOrCrash();
                if(doc.getSpecimenId() == null) {
                    return null;
                }

                panel = new JPanel(new FlowLayout(FlowLayout.CENTER));

                if(BiocodeService.getInstance().imageCache.containsKey(doc.getSpecimenId())) {
                    Image[] i = BiocodeService.getInstance().imageCache.get(doc.getSpecimenId());
                    if(i != null) {
                        panel.setLayout(new BorderLayout());
                        JScrollPane scroller = new JScrollPane(new ImagePanel(i));
                        panel.add(scroller);
                    }
                    else {
                        panel.add(new JLabel("This specimen has no photos"));
                    }
                    return panel;
                }

                JButton downloadButton = new JButton("    Download Pictures    ");
                panel.add(downloadButton);

                downloadButton.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        Runnable runnable = new Runnable() {
                            public void run() {
                                try {
                                    List<String> imageUrls = activeFIMSConnection.getImageUrls(doc);
                                    if(imageUrls != null && imageUrls.size() > 0) {
                                        Image[] images = new Image[imageUrls.size()];
                                        MediaTracker m = new MediaTracker(panel);
                                        for(int i=0; i < imageUrls.size(); i++) {
                                            System.out.println(imageUrls.get(i));
                                            URL imageUrl = new URL(imageUrls.get(i));
                                            Image img = Toolkit.getDefaultToolkit().createImage(imageUrl);
                                            images[i] = img;
                                            m.addImage(img,0);
                                        }

                                        try {
                                            m.waitForAll();
                                        }
                                        catch(InterruptedException ignored){}

                                        BiocodeService.getInstance().imageCache.put(doc.getSpecimenId(), images);

                                        ImagePanel imPanel = new ImagePanel(images);
                                        JScrollPane scroller = new JScrollPane(imPanel);
                                        panel.removeAll();
                                        panel.setLayout(new BorderLayout());
                                        panel.add(scroller);
                                        panel.revalidate();
                                    }
                                    else {
                                        BiocodeService.getInstance().imageCache.put(doc.getSpecimenId(), null);
                                        panel.removeAll();
                                        panel.add(new JLabel("This specimen has no photos"));
                                    }
                                } catch (IOException e1) {
                                    panel.removeAll();
                                    panel.add(new JLabel("Could not download photos: "+e1));
                                }
                                panel.revalidate();
                                panel.repaint();
                            }
                        };

                        panel.removeAll();
                        panel.add(new JLabel("Downloading images..."));
                        panel.revalidate();
                        panel.repaint();
                        new Thread(runnable).start();
                    }
                });



                return panel;
            }

            @Override
            public JComponent getPrintableComponent() {
                return panel;
            }
        };
    }

}
