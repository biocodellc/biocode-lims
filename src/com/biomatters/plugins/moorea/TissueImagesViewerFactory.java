package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.net.URL;
import java.io.InputStream;
import java.io.IOException;

import org.jdom.input.SAXBuilder;
import org.jdom.JDOMException;
import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
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
        return new DocumentViewer(){
            public JComponent getComponent() {
                final TissueDocument doc = (TissueDocument)annotatedDocuments[0].getDocumentOrCrash();
                if(doc.getSpecimenId() == null) {
                    return null;
                }

                final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));

                if(MooreaLabBenchService.imageCache.containsKey(doc.getSpecimenId())) {
                    Image i = MooreaLabBenchService.imageCache.get(doc.getSpecimenId());
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
                                    URL xmlUrl = new URL("http://calphotos.berkeley.edu/cgi-bin/img_query?getthumbinfo=1&specimen_no="+doc.getSpecimenId()+"&format=xml&num=all&query_src=lims");
                                    InputStream in = xmlUrl.openStream();
                                    SAXBuilder builder = new SAXBuilder();
                                    Element root = builder.build(in).detachRootElement();

                                    String imageUrlText = root.getChildText("enlarge_jpeg_url");
                                    if(imageUrlText != null) {
                                        URL imageUrl = new URL(imageUrlText);
                                        Image i = Toolkit.getDefaultToolkit().createImage(imageUrl);
                                        MediaTracker m = new MediaTracker(panel);
                                        m.addImage(i,0);
                                        try {
                                            m.waitForAll();
                                        }
                                        catch(InterruptedException ex){}

                                        MooreaLabBenchService.imageCache.put(doc.getSpecimenId(), i);

                                        ImagePanel imPanel = new ImagePanel(i);
                                        JScrollPane scroller = new JScrollPane(imPanel);
                                        panel.removeAll();
                                        panel.setLayout(new BorderLayout());
                                        panel.add(scroller);
                                        panel.revalidate();
                                    }
                                    else {
                                        MooreaLabBenchService.imageCache.put(doc.getSpecimenId(), null);
                                        panel.removeAll();
                                        panel.add(new JLabel("This specimen has no photos"));
                                    }
                                } catch (IOException e1) {
                                    panel.removeAll();
                                    panel.add(new JLabel("Could not download photos: "+e1));
                                } catch (JDOMException e1) {
                                    e1.printStackTrace();
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
        };
    }

    class ImagePanel extends JPanel {
        private Image image;

        public ImagePanel(Image i) {
            this.image = i;
        }

        public Dimension getPreferredSize() {
            return new Dimension(image.getWidth(this)+20, image.getHeight(this)+20);
        }

        public void paintComponent(Graphics g) {
            g.setColor(SystemColor.control.darker().darker());
            g.fillRect(0,0,getWidth(),getHeight());

            g.drawImage(image,10,10,this);
        }

    }
}
