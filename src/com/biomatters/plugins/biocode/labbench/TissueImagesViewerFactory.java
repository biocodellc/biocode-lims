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
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import com.biomatters.plugins.biocode.labbench.fims.MooreaFimsSample;
import org.jdom.input.SAXBuilder;
import org.jdom.JDOMException;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;

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
        TissueDocument tissueDoc = (TissueDocument)annotatedDocuments[0].getDocumentOrCrash();
        final Boolean isMooreaFims = MooreaFimsSample.class.isAssignableFrom(tissueDoc.getFimsSampleClass());
        if(!isMooreaFims) {
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

                if(BiocodeService.imageCache.containsKey(doc.getSpecimenId())) {
                    Image[] i = BiocodeService.imageCache.get(doc.getSpecimenId());
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
                                    List<String> imageUrls = isMooreaFims ? getImageUrlsCalphotos(doc) : getImageUrlsFlickr(doc);
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

                                        BiocodeService.imageCache.put(doc.getSpecimenId(), images);

                                        ImagePanel imPanel = new ImagePanel(images);
                                        JScrollPane scroller = new JScrollPane(imPanel);
                                        panel.removeAll();
                                        panel.setLayout(new BorderLayout());
                                        panel.add(scroller);
                                        panel.revalidate();
                                    }
                                    else {
                                        BiocodeService.imageCache.put(doc.getSpecimenId(), null);
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

            @Override
            public JComponent getPrintableComponent() {
                return panel;
            }
        };
    }

    private List<String> getImageUrlsCalphotos(TissueDocument doc) throws IOException, JDOMException {
        URL xmlUrl = new URL("http://calphotos.berkeley.edu/cgi-bin/img_query?getthumbinfo=1&specimen_no="+URLEncoder.encode(doc.getSpecimenId(), "UTF-8")+"&format=xml&num=all&query_src=lims");
        InputStream in = xmlUrl.openStream();
        SAXBuilder builder = new SAXBuilder();
        Element root = builder.build(in).detachRootElement();
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        out.output(root, System.out);
        List<Element> imageUrls = root.getChildren("enlarge_jpeg_url");
        List<String> result = new ArrayList<String>();
        for(Element e : imageUrls) {
            result.add(e.getText());
        }
        return result;
    }


    private List<String> getImageUrlsFlickr(TissueDocument doc) throws IOException, JDOMException {
        URL xmlUrl = new URL("http://www.flickr.com/services/rest/?method=flickr.photos.search&format=rest&machine_tags=bioValidator:specimen="+URLEncoder.encode("\""+doc.getSpecimenId()+"\"", "UTF-8")+"&api_key=724c92d972c3822bdb9c8ff501fb3d6a");
        System.out.println(xmlUrl);
        final HttpURLConnection urlConnection = (HttpURLConnection)xmlUrl.openConnection();
        urlConnection.setRequestMethod("GET");
        InputStream in = urlConnection.getInputStream();
        SAXBuilder builder = new SAXBuilder();
        Element root = builder.build(in).detachRootElement();
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        out.output(root, System.out);
        if(root.getName().equals("rsp") && "ok".equals(root.getAttributeValue("stat"))) {
            root = root.getChild("photos");
        }
        else {
            if(root.getChild("err") != null) {
                throw new IOException(root.getChild("err").getAttributeValue("msg"));
            }
            return Collections.emptyList();
        }
        if(root == null) {
            return Collections.emptyList();
        }
        List<Element> imageUrls = root.getChildren("photo");
        List<String> result = new ArrayList<String>();
        for(Element e : imageUrls) {
            result.add("http://farm"+e.getAttributeValue("farm")+".static.flickr.com/"+e.getAttributeValue("server")+"/"+e.getAttributeValue("id")+"_"+e.getAttributeValue("secret")+"_z.jpg");
        }
        return result;
    }

}
