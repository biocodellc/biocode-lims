package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.assembler.BatchChromatogramExportOperation;
import com.biomatters.plugins.biocode.assembler.SetReadDirectionOperation;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateFimsDataOperation;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateLimsDataOperation;
import com.biomatters.plugins.biocode.assembler.download.DownloadChromatogramsFromLimsOperation;
import com.biomatters.plugins.biocode.assembler.lims.AddAssemblyResultsToLimsOperation;
import com.biomatters.plugins.biocode.assembler.verify.VerifyTaxonomyDocumentViewerFactory;
import com.biomatters.plugins.biocode.assembler.verify.VerifyTaxonomyOperation;
import com.biomatters.plugins.biocode.assembler.verify.VerifyTaxonomyExporter;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import javax.swing.*;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @version $Id: BiocodePlugin.java 22212 2008-09-17 02:57:52Z richard $
 */
public class BiocodePlugin extends GeneiousPlugin {

    private File pluginUserDirectory;
    private static final Map<String, Icons> pluginIcons;
    static {
        pluginIcons = new HashMap<String, Icons>();
    }

    private static GeneiousActionOptions superBiocodeAction;

    public static GeneiousActionOptions getSuperBiocodeAction() {
        if (superBiocodeAction == null) {
            superBiocodeAction = new GeneiousActionOptions("Biocode", null, getIcons("biocode24.png"))
                    .setInMainToolbar(true, 0.532)
                    .setMainMenuLocation(GeneiousActionOptions.MainMenu.Sequence);
            superBiocodeAction.addSubmenuDivider(0.1);
            superBiocodeAction.addSubmenuDivider(0.2);
            superBiocodeAction.addSubmenuDivider(0.5);
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

    @Override
    public String getEmailAddressForCrashes() {
        return "support@mooreabiocode.org";
    }

    public String getVersion() {
        return "2.1";
    }

    public int getMaximumApiVersion() {
        return 4;   // __MAJOR_API_VERSION__
    }

    public String getMinimumApiVersion() {
        return "4.16";   // __API_VERSION__
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

        URL reportingIcon = BiocodePlugin.class.getResource("reporting.png");
        putUrlIntoIconsMap(reportingIcon, "reporting.png");

        URL splitGelIcon = BiocodePlugin.class.getResource("splitgel16.png");
        putUrlIntoIconsMap(splitGelIcon, "splitgel16.png");

        URL biocodeIconS = BiocodePlugin.class.getResource("biocode16.png");
        URL biocodeIconL = BiocodePlugin.class.getResource("biocode24.png");
        putUrlIntoIconsMap(biocodeIconS, biocodeIconL, "biocode24.png");

        URL cherryIconS = BiocodePlugin.class.getResource("cherry_16.png");
        URL cherryIconL = BiocodePlugin.class.getResource("cherry_24.png");
        putUrlIntoIconsMap(cherryIconS, cherryIconL, "cherry_24.png");
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
                new VerifyTaxonomyDocumentViewerFactory(),
                new CherryPickingDocumentViewerFactory(),
                new MultiLocusDocumentViewerFactory(),
                new TabularPlateDocumentViewerFactory()
        };
    }

    @Override
    public DocumentOperation[] getDocumentOperations() {
        ArrayList<DocumentOperation> operations = new ArrayList<DocumentOperation>(Arrays.asList( new DocumentOperation[] {
                new CherryPickingDocumentOperation(),
                new NewPlateDocumentOperation(),
                new DownloadChromatogramsFromLimsOperation(false),
                //new SetReadDirectionOperation(),
                new BatchChromatogramExportOperation(),
                new VerifyTaxonomyOperation(),
                new AnnotateLimsDataOperation(),
                new AnnotateFimsDataOperation(),
                new AddAssemblyResultsToLimsOperation(true, false),
                new AddAssemblyResultsToLimsOperation(false, false),
//                new ExportForBarstoolOperation(false)
        }));
        if(Geneious.getMinorApiVersion() < 40) {  //we moved the set read direction operation into the assembly plugin in version 4.40 of the API
            operations.add(new SetReadDirectionOperation());    
        }
        return operations.toArray(new DocumentOperation[operations.size()]);
    }

    @Override
    public DocumentType[] getDocumentTypes() {
        return new DocumentType[] {
                new DocumentType<TissueDocument>("Tissue Sample", TissueDocument.class, BiocodePlugin.getIcons("specimenDocument_24.png")),
                new DocumentType<WorkflowDocument>("Workflow Document", WorkflowDocument.class, BiocodePlugin.getIcons("workflowDocument_32.png")),
                new DocumentType<PlateDocument>("Plate Document", PlateDocument.class, BiocodePlugin.getIcons("plateDocument_32.png"))
        };
    }

    @Override
    public DocumentFileExporter[] getDocumentFileExporters() {
        return new DocumentFileExporter[] {new WorkflowSummaryExporter(), new PlateExporter(), new VerifyTaxonomyExporter()};
    }
}
