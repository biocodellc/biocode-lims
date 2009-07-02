package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;

import javax.swing.*;
import java.io.File;
import java.util.Map;
import java.util.HashMap;
import java.net.URL;
import java.net.URISyntaxException;
import java.awt.*;

/**
 * @version $Id: MooreaLabBenchPlugin.java 22212 2008-09-17 02:57:52Z richard $
 */
public class MooreaLabBenchPlugin extends GeneiousPlugin {
    private File pluginUserDirectory;
    private File pluginDirectory;
    public static final Map<String, Icons> pluginIcons;
    static {
        pluginIcons = new HashMap<String, Icons>();
    }

    public String getName() {
        return "Moorea Lab Bench Plugin";
    }

    public String getDescription() {
        return "";
    }

    public String getHelp() {
        return "";
    }

    public String getAuthors() {
        return "Biomatters Ltd.";
    }

    public String getVersion() {
        return "1.0";
    }

    public String getMinimumApiVersion() {
        return "4.0";
    }

    public int getMaximumApiVersion() {
        return 4;
    }

    @Override
    public void initialize(File pluginUserDirectory, File pluginDirectory) {
        this.pluginUserDirectory = pluginUserDirectory;
        this.pluginDirectory = pluginDirectory;
        Runnable r = new Runnable(){
            public void run() {
                initialiseIcons();
            }
        };
        new Thread(r).start();
    }

    private static void initialiseIcons() {
        URL thermocycleIcon = MooreaLabBenchPlugin.class.getResource("thermocycle_16.png");
        putUrlIntoIconsMap(thermocycleIcon, "thermocycle_16.png");

        URL imageIcon = MooreaLabBenchPlugin.class.getResource("addImage_16.png");
        putUrlIntoIconsMap(imageIcon, "addImage_16.png");
    }

    private static void putUrlIntoIconsMap(URL url, String key){
        if(url == null) {
            assert false : url.toString();
            return;
        }
        ImageIcon icon = new ImageIcon(url);
        Icons icons = new Icons(icon);
        pluginIcons.put(key, icons);
    }

    public static Icons getIcons(String name) {
        return pluginIcons.get(name);
    }

    @Override
    public GeneiousService[] getServices() {
        MooreaLabBenchService service = MooreaLabBenchService.getInstance();
        service.setDataDirectory(pluginUserDirectory);
        return new GeneiousService[] {service};
    }

    @Override
    public DocumentViewerFactory[] getDocumentViewerFactories() {
        return new DocumentViewerFactory[] {
                new TissueImagesViewerFactory(),
                new TissueSampleViewerFactory(true),
                new TissueSampleViewerFactory(false),
                new PlateDocumentViewerFactory(),
                new WorkflowDocumentViewerFactory(),
                new MultiWorkflowDocumentViewerFactory()
        };
    }

    @Override
    public DocumentOperation[] getDocumentOperations() {
        return new DocumentOperation[] {
                new NewPlateDocumentOperation()
        };
    }

    @Override
    public DocumentType[] getDocumentTypes() {
        return new DocumentType[] {
                new DocumentType("Tissue Sample", TissueDocument.class, IconUtilities.getIcons("specimenDocument.png"))
        };
    }
}
