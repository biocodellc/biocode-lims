package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.LoginOptions;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnection;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import com.biomatters.plugins.biocode.utilities.SharedCookieHandler;

import javax.swing.*;
import javax.ws.rs.ProcessingException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * @author Matthew Cheung
 * @version $Id$
 *       <p />
 *       Created on 1/02/14 10:50 AM
 */public class BiocodeFIMSConnectionOptions extends PasswordOptions {

    private StringOption hostOption;
    private ComboBoxOption<ProjectOptionValue> projectOption;

    private static final String DEFAULT_HOST = "http://biscicol.org";

    public BiocodeFIMSConnectionOptions() {
        super(BiocodePlugin.class);
        hostOption = addStringOption("host", "Host:", DEFAULT_HOST);
        final StringOption usernameOption = addStringOption("username", "Username:", "");
        final PasswordOption passwordOption = addCustomOption(new PasswordOption("password", "Password:", true));
        addButtonOption("authenticate", "", "Authenticate").addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final ProgressFrame progressFrame = new ProgressFrame("Authenticating...", "", Dialogs.getCurrentModalDialog());
                progressFrame.setCancelable(false);
                progressFrame.setIndeterminateProgress();
                Thread thread = new Thread() {
                    public void run() {
                        try {
                            login(hostOption.getValue(), usernameOption.getValue(), passwordOption.getPassword());
                        } catch (MalformedURLException e) {
                            Dialogs.showMessageDialog("Could not connect to server.  Invalid URL: " + e.getMessage(), "Invalid URL");
                        } catch (ProcessingException e) {
                            Dialogs.showMessageDialog(
                                    "There was a problem communicating with the server: " + e.getMessage(),
                                    "Connection Error"
                            );
                        } catch (DatabaseServiceException e) {
                            Dialogs.showMessageDialog(e.getMessage(), "Login Failed");
                        }
                        progressFrame.setComplete();
                    }
                };
                thread.start();
            }
        });
        addDivider(" ");

        final List<ProjectOptionValue> projectOptions = new ArrayList<ProjectOptionValue>();
        List<Project> projectCache = getProjectCache();
        if (projectCache == null || projectCache.isEmpty()) {
            projectOptions.add(ProjectOptionValue.NO_VALUE);
        } else {
            for (Project project : projectCache) {
                projectOptions.add(new ProjectOptionValue(project));
            }
        }

        projectOption = addComboBoxOption("project", "Project:", projectOptions, projectOptions.get(0));
    }

    public void login(String host, String username, String password) throws MalformedURLException, ProcessingException, DatabaseServiceException {
        URL url = new URL(host);
        SharedCookieHandler.registerHost(url.getHost());
        BiocodeFIMSClient client = new BiocodeFIMSClient(host, LoginOptions.DEFAULT_TIMEOUT);
        client.login(username, password);
        loadProjectsFromServer(client);
    }

    private void loadProjectsFromServer(BiocodeFIMSClient client) {
        try {
            List<Project> projects = client.getProjects();
            cacheProjects(projects);

            final List<ProjectOptionValue> optionValues = new ArrayList<ProjectOptionValue>();
            for (Project project : projects) {
                optionValues.add(new ProjectOptionValue(project));
            }

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    projectOption.setPossibleValues(optionValues);
                }
            });

        } catch (DatabaseServiceException e) {
            BiocodeUtilities.displayExceptionDialog("Failed to Load Projects",
                    "Failed to load project list from " + BiocodeFIMSConnection.HOST + ": " + e.getMessage(), e, null);
        }
    }

    private static final String CACHE_NAME = "cachedProjects";
    private static final String ID = "id";
    private static final String CODE = "code";
    private static final String TITLE = "title";
    private static final String XML = "xmlConfigLocation";

    /**
     * Stores expeditions to a cache to be retrieved when Options are created to avoid the delay that is
     * required to query the web service for the live list of expeditions.
     *
     * @param projects
     */
    private void cacheProjects(List<Project> projects) {
        try {
            Preferences cacheNode = getCacheNode();
            cacheNode.clear();
            for (Project project : projects) {
                Preferences childNode = cacheNode.node(project.code);
                childNode.putInt(ID, project.id);
                childNode.put(CODE, project.code);
                childNode.put(TITLE, project.title);
                childNode.put(XML, project.xmlLocation);
            }
            cacheNode.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();  // Won't be able to store anything in the cache.  Oh well
        }
    }

    /**
     *
     * @return A list of {@link Project}s retrieved previously or null if the cache is empty or if there
     * is a problem retrieving the cache from preferences
     */
    private List<Project> getProjectCache() {
        try {
            List<Project> fromCache = new ArrayList<Project>();
            Preferences cacheNode = getCacheNode();
            String[] children = cacheNode.childrenNames();
            if(children == null || children.length == 0) {
                return null;
            }
            for (String child : children) {
                Preferences projectNode = cacheNode.node(child);
                int id = projectNode.getInt(ID, -1);
                String code = projectNode.get(CODE, null);
                String title = projectNode.get(TITLE, null);
                String xml = projectNode.get(XML, null);
                if(id != -1 && code != null && title != null && xml != null) {
                    fromCache.add(new Project(id, code, title, xml));
                }
            }
            return fromCache;
        } catch (BackingStoreException e) {
            e.printStackTrace();
            return null;  // Won't be able to use the cache, but oh well.
        }
    }

    private Preferences getCacheNode() {
        Preferences preferences = Preferences.userNodeForPackage(BiocodeFIMSConnection.class);
        return preferences.node(CACHE_NAME);
    }

    private static class ProjectOptionValue extends OptionValue {
        Project project;

        ProjectOptionValue(Project project) {
            super(project == null ? "noValue" :  project.code,
                    project == null ? "Please login to retrieve projects " : project.title);
            this.project = project;
        }

        static final ProjectOptionValue NO_VALUE = new ProjectOptionValue(null);
    }

    private static final List<OptionValue> NO_FIELDS = Collections.singletonList(new Options.OptionValue("None", "None"));
    public List<OptionValue> getFieldsAsOptionValues() throws DatabaseServiceException {
        List<OptionValue> fields = new ArrayList<OptionValue>();
        Project project = projectOption.getValue().project;
        if(project == null) {
            return NO_FIELDS;
        }
        for (Project.Field field : project.getFields()) {
            fields.add(new OptionValue(TableFimsConnection.CODE_PREFIX + field.uri, field.name));
        }
        return fields;
    }

    public Project getProject() {
        return projectOption.getValue().project;
    }

    public String getHost() {
        return hostOption.getValue();
    }
}