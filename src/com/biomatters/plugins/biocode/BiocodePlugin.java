package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.assembler.BatchChromatogramExportOperation;
import com.biomatters.plugins.biocode.assembler.SetReadDirectionOperation;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateFimsDataOperation;
import com.biomatters.plugins.biocode.assembler.download.DownloadChromatogramsFromLimsOperation;
import com.biomatters.plugins.biocode.assembler.lims.AddAssemblyResultsToLimsOperation;
import com.biomatters.plugins.biocode.assembler.verify.VerifyTaxonomyDocumentViewerFactory;
import com.biomatters.plugins.biocode.assembler.verify.VerifyTaxonomyOperation;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.submission.bold.ExportForBoldOperation;
import com.biomatters.plugins.biocode.submission.genbank.barstool.ExportForBarstoolOperation;

import javax.swing.*;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * @version $Id: BiocodePlugin.java 22212 2008-09-17 02:57:52Z richard $
 */
public class BiocodePlugin extends GeneiousPlugin {

    private File pluginUserDirectory;
    public static final Map<String, Icons> pluginIcons;
    static {
        pluginIcons = new HashMap<String, Icons>();
    }

    private static GeneiousActionOptions superBiocodeAction;

    public static GeneiousActionOptions getSuperBiocodeAction() {
        if (superBiocodeAction == null) {
            superBiocodeAction = new GeneiousActionOptions("Biocode", null, getIcons("biocode24.png"))
                    .setInMainToolbar(true, 0.532)
                    .setMainMenuLocation(GeneiousActionOptions.MainMenu.Sequence);
            superBiocodeAction.addSubmenuDivider(0.5);
            superBiocodeAction.addSubmenuDivider(0.65);
        }
        return superBiocodeAction;
    }

    public String getName() {
        return "Biocode Plugin";
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
        return "1.3.2";
    }

    public String getMinimumApiVersion() {
        return "4.10";
    }

    public int getMaximumApiVersion() {
        return 4;
    }

    @Override
    public void initialize(File pluginUserDirectory, File pluginDirectory) {
        this.pluginUserDirectory = pluginUserDirectory;
        Runnable r = new Runnable(){
            public void run() {
                initialiseIcons();
            }
        };
        new Thread(r).start();
    }

    private static void initialiseIcons() {
        URL thermocycleIcon = BiocodePlugin.class.getResource("thermocycle_16.png");
        putUrlIntoIconsMap(thermocycleIcon, "thermocycle_16.png");

        URL imageIcon = BiocodePlugin.class.getResource("addImage_16.png");
        putUrlIntoIconsMap(imageIcon, "addImage_16.png");

        URL reactionIcon = BiocodePlugin.class.getResource("newReaction_24.png");
        putUrlIntoIconsMap(reactionIcon, "newReaction_24.png");

        URL specimenIcon = BiocodePlugin.class.getResource("specimenDocument_24.png");
        putUrlIntoIconsMap(specimenIcon, "specimenDocument_24.png");

        URL bulkEditIcon = BiocodePlugin.class.getResource("bulkEdit_16.png");
        putUrlIntoIconsMap(bulkEditIcon, "bulkEdit_16.png");

        URL workflowDocumentIcon = BiocodePlugin.class.getResource("workflowDocument_32.png");
        putUrlIntoIconsMap(workflowDocumentIcon, "workflowDocument_32.png");

        URL plateDocumentIcon = BiocodePlugin.class.getResource("plateDocument_32.png");
        putUrlIntoIconsMap(plateDocumentIcon, "plateDocument_32.png");

        URL swapDirectionIcon = BiocodePlugin.class.getResource("swapDirection_16.png");
        putUrlIntoIconsMap(swapDirectionIcon, "swapDirection_16.png");

        URL barcodeIcon = BiocodePlugin.class.getResource("barcode_16.png");
        putUrlIntoIconsMap(barcodeIcon, "barcode_16.png");

        URL workflowIcon = BiocodePlugin.class.getResource("workflow_16.png");
        putUrlIntoIconsMap(workflowIcon, "workflow_16.png");

        URL abiIcon = BiocodePlugin.class.getResource("abi_16.png");
        putUrlIntoIconsMap(abiIcon, "abi_16.png");

        URL biocodeIconS = BiocodePlugin.class.getResource("biocode16.png");
        URL biocodeIconL = BiocodePlugin.class.getResource("biocode24.png");
        putUrlIntoIconsMap(biocodeIconS, biocodeIconL, "biocode24.png");
    }

    private static void putUrlIntoIconsMap(URL url, String key){
        if(url == null) {
            assert false;
            return;
        }
        ImageIcon icon = new ImageIcon(url);
        Icons icons = new Icons(icon);
        pluginIcons.put(key, icons);
    }

    private static void putUrlIntoIconsMap(URL urlSmall, URL urlLarge, String key){
        if(urlSmall == null || urlLarge == null) {
            assert false;
            return;
        }
        ImageIcon iconSmall = new ImageIcon(urlSmall);
        ImageIcon iconLarge = new ImageIcon(urlLarge);
        Icons icons = new Icons(iconSmall, iconLarge);
        pluginIcons.put(key, icons);
    }

    public static Icons getIcons(String name) {
        return pluginIcons.get(name);
    }

    @Override
    public GeneiousService[] getServices() {
        BiocodeService service = BiocodeService.getInstance();
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
                new MultiWorkflowDocumentViewerFactory(),
                new MultiPrimerDocumentViewerFactory(Reaction.Type.PCR),
                new MultiPrimerDocumentViewerFactory(Reaction.Type.CycleSequencing),
                new VerifyTaxonomyDocumentViewerFactory()
        };
    }

    @Override
    public DocumentOperation[] getDocumentOperations() {
        return new DocumentOperation[] {
                new NewPlateDocumentOperation(),
                new DownloadChromatogramsFromLimsOperation(false),
                new SetReadDirectionOperation(),
                new BatchChromatogramExportOperation(),
                new VerifyTaxonomyOperation(),
                new AnnotateFimsDataOperation(),
                new AddAssemblyResultsToLimsOperation(true, false),
                new AddAssemblyResultsToLimsOperation(false, false),
                new ExportForBoldOperation(),
                new ExportForBarstoolOperation(false)
        };
    }

    @Override
    public DocumentType[] getDocumentTypes() {
        return new DocumentType[] {
                new DocumentType("Tissue Sample", TissueDocument.class, BiocodePlugin.getIcons("specimenDocument_24.png")),
                new DocumentType("Workflow Document", WorkflowDocument.class, BiocodePlugin.getIcons("workflowDocument_32.png")),
                new DocumentType("Plate Document", PlateDocument.class, BiocodePlugin.getIcons("plateDocument_32.png"))
        };
    }
}
