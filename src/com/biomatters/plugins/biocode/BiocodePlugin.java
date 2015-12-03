package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.assembler.BatchChromatogramExportOperation;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateFimsDataOperation;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateLimsDataOperation;
import com.biomatters.plugins.biocode.assembler.download.DownloadChromatogramsFromLimsOperation;
import com.biomatters.plugins.biocode.assembler.lims.AddAssemblyResultsToLimsOperation;
import com.biomatters.plugins.biocode.assembler.lims.MarkSequencesAsSubmittedInLimsOperation;
import com.biomatters.plugins.biocode.assembler.verify.VerifyTaxonomyDocumentViewerFactory;
import com.biomatters.plugins.biocode.assembler.verify.VerifyTaxonomyExporter;
import com.biomatters.plugins.biocode.assembler.verify.VerifyTaxonomyOperation;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

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
    public static final String PLUGIN_VERSION = "2.9.2";
    public static final String SUPPORT_EMAIL = "support@mooreabiocode.org";

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
        return SUPPORT_EMAIL;
    }

    public String getVersion() {
        return PLUGIN_VERSION;
    }

    public int getMaximumApiVersion() {
        return 4;
    }

    public String getMinimumApiVersion() {
        return "4.611";  // We require ProgressFrame.setCancelable.  If we ever make this require 4.700 or later, make VerifyTaxonomyOperation not modify input documents when PluginUtilities.isRunningFromScript() and remove the exclusion for VerifyTaxonomyOperation in WorkflowSupportTest
    }

    private class NewVersionAvailableDialogOptions extends Options {
        private MultipleLineStringOption releaseNotesDisplay;

        NewVersionAvailableDialogOptions(String latestVersion,
                                         String latestVersionURL,
                                         String releaseNotes,
                                         String extraInformation) throws IOException, JDOMException {

            addLabel("<html>There is a new version of the Biocode plugin available (" + latestVersion + "). " +
                    "You are<br> " +
                    "using " + getVersion() + ". If you would like to upgrade, please visit:<br> " +
                    "<a href=\"" + latestVersionURL + "\">" + latestVersionURL + "</a><br><br></html>");

            releaseNotesDisplay = addMultipleLineStringOption("releaseNotes", "", releaseNotes, 10, true);

            if (!extraInformation.isEmpty()) {
                addLabel(createHtmlWithWidth(extraInformation, 70));
            }
        }

        private String createHtmlWithWidth(String extraInformation, int width) {
            String tmp = extraInformation.replaceAll("(\\s)+", " ");
            if (tmp == null || tmp.length() == 0)
                return "";

            StringBuilder sb = new StringBuilder("<html><br>Note: ");
            int start = 0;
            int end;
            while (start < tmp.length()) {
                end = start + width;
                if (end >= tmp.length() - 1) {
                    sb.append("<br>").append(tmp.substring(start));
                    break;
                }

                int indexOfLastSpace = tmp.substring(start, end).lastIndexOf(" ");
                if(indexOfLastSpace == -1) {
                    // If for some reason we have a 70 length word we'll just stop here and add the rest of the text on one line
                    sb.append("<br>").append(tmp.substring(start));
                    break;
                }
                end = start + indexOfLastSpace;
                sb.append("<br>").append(tmp.substring(start, end));
                start = end;
            }

            sb.append("</html>");
            return sb.toString();
        }


        @Override
        protected JPanel createPanel() {
            JPanel panel = super.createPanel();
            JScrollPane scrollPane = releaseNotesDisplay.getComponent();
            Component component = scrollPane.getViewport().getComponent(0);
            ((JTextArea)component).setEditable(false);
            return panel;
        }
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

        if (compareVersions(Geneious.getVersion(), "8.1.0") < 0) {
            checkUpdate();
        }
    }

    private void checkUpdate() {
        final String pluginVersionsXmlURL = "http://desktop-links.geneious.com/assets/plugins/biocode/PluginVersions.xml?" +
                "Version=" + getVersion() +
                "&OS=" + System.getProperty("os.name").replace(" ", "_") + "_" +
                System.getProperty("os.version", "").replace(" ", "_") +
                "&OSArch=" + System.getProperty("os.arch").replace(" ", "_");

        Runnable r2 = new Runnable(){
            public void run() {
                try {
                    SAXBuilder builder = new SAXBuilder();
                    Document document = builder.build(new URL(pluginVersionsXmlURL));

                    String latestVersion = document.getRootElement().getChildText("LatestVersion");
                    String latestVersionURL = document.getRootElement().getChildText("LatestVersionURL");
                    String releaseNotes = document.getRootElement().getChildText("ReleaseNotes");
                    String extraInformation = document.getRootElement().getChildText("ExtraInformation");
                    if (latestVersion != null && compareVersions(getVersion(), latestVersion) < 0) {
                        final Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[]{"OK"}, "New Biocode Plugin Available");
                        final NewVersionAvailableDialogOptions newVersionAvailableDialogOptions =
                                new NewVersionAvailableDialogOptions(latestVersion, latestVersionURL, releaseNotes, extraInformation);

                        ThreadUtilities.invokeAndWait(new Runnable() {
                            @Override
                            public void run() {
                                Dialogs.showDialog(dialogOptions, newVersionAvailableDialogOptions.getPanel());
                            }
                        });
                    }
                } catch (MalformedURLException e) {
                    throw new IllegalStateException("Invalid URL", e);
                } catch (JDOMException e) {
                    Dialogs.showMessageDialog("Failed to show updates for Biocode Plugin." + e.getMessage());
                } catch (IOException e) {
                    Dialogs.showMessageDialog("Failed to show updates for Biocode Plugin." + e.getMessage());
                } catch (InterruptedException e) {
                    // Thread interrupted.
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
        };
        long lastRun = Preferences.userNodeForPackage(BiocodePlugin.class).getLong("LastUpgradeCheck", 0);

        if(System.currentTimeMillis() - lastRun > 1000 * 60 * 60 * 24) {
            Preferences.userNodeForPackage(BiocodePlugin.class).putLong("LastUpgradeCheck", System.currentTimeMillis());
            new Thread(r2, "Checking for update versions of the biocode plugin").start();
        }
    }


    /**
     *
     * @param version1 program version of the form nnn[.nnn[.nnn]]
     * @param version2 program version of the form nnn[.nnn[.nnn]]
     * @return 0 if the two versions are equal, or a negative number if version1 < version2
     * or a positive number of version1 > version2.
     */
    public static int compareVersions(String version1, String version2) {
        String[]ver1 = version1.split("\\.");
        String[]ver2 = version2.split("\\.");

        for(int i=0; i < Math.max(ver1.length,ver2.length); i++){
            if(ver1.length > i && ver2.length > i){
                if(Integer.parseInt(ver1[i]) > Integer.parseInt(ver2[i])){
                    return 1;
                }
                if(Integer.parseInt(ver1[i]) < Integer.parseInt(ver2[i])){
                    return -1;
                }
            }

            else if(ver1.length <= i){
                return -1;
            }
            else if(ver2.length <= i){
               return 1;
            }
        }
        return 0;
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

        URL biocodeIconConnected = BiocodePlugin.class.getResource("biocode16_connected.png");
        putUrlIntoIconsMap(biocodeIconConnected, biocodeIconConnected, "biocode16_connected.png");

        URL biocodeIconDisconnected = BiocodePlugin.class.getResource("biocode16_disconnected.png");
        putUrlIntoIconsMap(biocodeIconDisconnected, biocodeIconDisconnected, "biocode16_disconnected.png");

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
        return new DocumentOperation[] {
//                new FillInTaxonomyOperation(),
                new CherryPickingDocumentOperation(),
                new NewPlateDocumentOperation(),
                new DownloadChromatogramsFromLimsOperation(false),
                new BatchChromatogramExportOperation(),
                new VerifyTaxonomyOperation(),
                new AnnotateLimsDataOperation(),
                new AnnotateFimsDataOperation(),
                new AddAssemblyResultsToLimsOperation(true, false),
                new AddAssemblyResultsToLimsOperation(false, false),
                new MarkSequencesAsSubmittedInLimsOperation(),
//                new WorkflowBuilder(),  // Used as a one off to import raw Biocode data into Darwin
//                new MetagenomicsDocumentOperation()
                //new ImportLimsOperation()
//                new ExportForBarstoolOperation(false)
        };
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
        return new DocumentFileExporter[] {new WorkflowSummaryExporter(), new PlateExporter(), new VerifyTaxonomyExporter(), new CherryPickingTableExporter(),/* new CsvAnnotationExporter()*/};
    }

//    @Override
//    public DocumentFileImporter[] getDocumentFileImporters() {
//        return new DocumentFileImporter[] {new BoldTsvImporter()};
//    }
}
