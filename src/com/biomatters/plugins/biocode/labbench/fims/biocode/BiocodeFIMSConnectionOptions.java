package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.xml.FastSaxBuilder;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnection;
import org.jdom.Element;
import org.jdom.JDOMException;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Created by matthew on 1/02/14.
 */
public class BiocodeFIMSConnectionOptions extends PasswordOptions {

    ComboBoxOption<ExpeditionOptionValue> expeditionOption;

    public BiocodeFIMSConnectionOptions() {
        super(BiocodePlugin.class);

        final List<ExpeditionOptionValue> expeditionOptions = new ArrayList<ExpeditionOptionValue>();
        List<Expedition> expeditionCache = getExpeditionCache();
        if(expeditionCache == null) {
            expeditionOptions.add(new ExpeditionOptionValue(new Expedition(1, "IndoP",
                    "IndoPacific Database", "https://biocode-fims.googlecode.com/svn/trunk/Documents/IndoPacific/indoPacificConfiguration.xml")));
        } else {
            for (Expedition expedition : expeditionCache) {
                expeditionOptions.add(new ExpeditionOptionValue(expedition));
            }
        }
        expeditionOption = addComboBoxOption("expedition", "Expedition:", expeditionOptions, expeditionOptions.get(0));

        new Thread() {
            public void run() {
                try {
                    List<Expedition> expeditions = BiocodeFIMSUtils.getExpeditions();
                    cacheExpeditions(expeditions);

                    final List<ExpeditionOptionValue> optionValues = new ArrayList<ExpeditionOptionValue>();
                    for (Expedition expedition : expeditions) {
                        optionValues.add(new ExpeditionOptionValue(expedition));
                    }

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            expeditionOption.setPossibleValues(optionValues);
                        }
                    });
                } catch (DatabaseServiceException e) {
                    Dialogs.showMessageDialog("Failed to load expedition list from " + BiocodeFIMSConnection.HOST);
                }
            }
        }.start();
    }

    private static final String CACHE_NAME = "cachedExpeditions";
    private static final String ID = "id";
    private static final String CODE = "code";
    private static final String TITLE = "title";
    private static final String XML = "xmlConfigLocation";

    /**
     * Stores expeditions to a cache to be retrieved when Options are created to avoid the delay that is
     * required to query the web service for the live list of expeditions.
     *
     * @param expeditions
     */
    private void cacheExpeditions(List<Expedition> expeditions) {
        try {
            Preferences cacheNode = getCacheNode();
            cacheNode.clear();
            for (Expedition expedition : expeditions) {
                Preferences childNode = cacheNode.node(expedition.code);
                childNode.putInt(ID, expedition.id);
                childNode.put(CODE, expedition.code);
                childNode.put(TITLE, expedition.title);
                childNode.put(XML, expedition.xmlLocation);
            }
            cacheNode.flush();
        } catch (BackingStoreException e) {
            e.printStackTrace();  // Won't be able to store anything in the cache.  Oh well
        }
    }

    /**
     *
     * @return A list of {@link Expedition}s retrieved previously or null if the cache is empty or if there
     * is a problem retrieving the cache from preferences
     */
    private List<Expedition> getExpeditionCache() {
        try {
            List<Expedition> fromCache = new ArrayList<Expedition>();
            Preferences cacheNode = getCacheNode();
            String[] children = cacheNode.childrenNames();
            if(children == null || children.length == 0) {
                return null;
            }
            for (String child : children) {
                Preferences expeditionNode = cacheNode.node(child);
                int id = expeditionNode.getInt(ID, -1);
                String code = expeditionNode.get(CODE, null);
                String title = expeditionNode.get(TITLE, null);
                String xml = expeditionNode.get(XML, null);
                if(id != -1 && code != null && title != null && xml != null) {
                    fromCache.add(new Expedition(id, code, title, xml));
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

    private static class ExpeditionOptionValue extends OptionValue {
        Expedition expedition;

        ExpeditionOptionValue(Expedition expedition) {
            super(expedition.code, expedition.title);
            this.expedition = expedition;
        }
    }


    public List<OptionValue> getFieldsAsOptionValues() throws DatabaseServiceException {
        List<OptionValue> fields = new ArrayList<OptionValue>();
        for (Expedition.Field field : expeditionOption.getValue().expedition.getFields()) {
            // todo Should we be using the uri of the column.  ie darwin core term
            fields.add(new OptionValue(TableFimsConnection.CODE_PREFIX + field.name, field.name));
        }
        return fields;
    }



    public Expedition getExpedition() {
        return expeditionOption.getValue().expedition;
    }
}
