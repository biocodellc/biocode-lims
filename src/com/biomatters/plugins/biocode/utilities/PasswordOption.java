package com.biomatters.plugins.biocode.utilities;

import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.jdom.Element;

import javax.crypto.*;
import javax.crypto.spec.DESKeySpec;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.StringTokenizer;
import java.util.logging.Logger;


/**
 * An option that stores a user entered password.  A checkbox gives the user the choice of saving the password (encrypted) to the registry.
 * <b>Note: </b>Do not use {@link #getValue()} to get the password.  It is not guaranteed to return anything other than an empty string.  Instead use {@link #getPassword()}
 * <br/><br/>
 * The password is stored encrypted so that users can't simply view passwords in their preferences file.
 * <br/><br/><strong>Note</strong>: The password should not be considered secure.  The key for decryption can easily be obtained
 * by viewing this source code or by decrypting the compiled class.
 */
public class PasswordOption extends Options.Option<String, JPanel> {
    private boolean updatingComponent;
    private boolean saveCheckboxVisible = true;

    /**
     * Construct a new PasswordOption
     * @param name the name to be used for referencing this option. For example from scripts or source code which wish to programmatically get or set the value of this option.
     * Names should be in standard Java variable name style eg. "myAwesomeOption".
     * @param label a label describing this option to be displayed to the user. eg. "My Awesome Option"
     * @param showSaveCheckbox whether or not to show the save checkbox. if true, the value of this option will only be saved to preferences
     * if the user checks the checkbox. If false, the value will be saved to preferences as per usual.
     */
    public PasswordOption(String name, String label, boolean showSaveCheckbox) {
        super(name, label, "");
        setFillHorizontalSpace(true);
        saveCheckboxVisible = showSaveCheckbox;
    }

    private static final String ENCODED = "{\\_GPW_/}";  // Chances of someone having a password starting with this is extremely low
    public String getValueFromString(String value) {
        return value;
    }

    @Override
    public String getValueAsString(String value) {
        return value;
    }

    @Override
    public void setRestorePreferenceApplies(boolean restorePreferenceApplies) {
        //do nothing - this is for security reasons...
    }

    @Override
    public Element toXML() {
        Element element = super.toXML();
        if (saveCheckboxVisible) {
            element.addContent(new Element("saveCheckboxVisible"));
        }
        return element;
    }

    /**
     * Constructor for XML Serialization. See {@link com.biomatters.geneious.publicapi.documents.XMLSerializable}
     */
    public PasswordOption(Element element) throws XMLSerializationException {
        super(element);
        this.saveCheckboxVisible = element.getChild("saveCheckboxVisible") != null;
        this.updatingComponent = false;
    }

    protected void setValueOnComponent(JPanel component, String value) {
        if (updatingComponent) return;
        updatingComponent = true;
        ((JPasswordField)component.getComponent(0)).setText(decrypt(value));
        updatingComponent = false;
    }

    @Override
    protected void handleSetEnabled(JPanel component, boolean enabled) {
        super.handleSetEnabled(component, enabled);
        for(Component child : component.getComponents()) {
            child.setEnabled(enabled);
        }
    }

    protected JPanel createComponent() {
        JPanel panel = new GPanel(new BorderLayout());
        final JPasswordField field = new JPasswordField();
        if(getValue().length() > 0) {
            String decryptedValue = decrypt(getValue());
            field.setText(decryptedValue);
        }
        field.setColumns(15);
        field.setMinimumSize(field.getPreferredSize());
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                if (updatingComponent) return;
                updatingComponent = true;
                setPassword(new String(field.getPassword()));
                updatingComponent = false;
            }

            public void insertUpdate(DocumentEvent e) {
                update();
            }

            public void removeUpdate(DocumentEvent e) {
                update();
            }

            public void changedUpdate(DocumentEvent e) {
                update();
            }
        });
        panel.add(field, BorderLayout.CENTER);
        if (saveCheckboxVisible) {
            boolean save = getValue().length() > 0;
            final JCheckBox saveBox = new JCheckBox("Save", save);
            panel.add(saveBox, BorderLayout.EAST);
            ChangeListener saveListener = new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    PasswordOption.super.setRestorePreferenceApplies(saveBox.isSelected());
                    PasswordOption.super.setShouldSaveValue(saveBox.isSelected());
                }
            };
            saveBox.addChangeListener(saveListener);
            saveListener.stateChanged(null);
        }
        return panel;
    }

    /**
     * Get the decrypted password. Call {@link #getValue()} returns the encrypted value
     *
     * @return the decrypted password
     */
    public String getPassword() {
        return decrypt(getValue());
    }

    private static final Logger log = Logger.getLogger(PasswordOption.class.getName());

    /**
     * Set the un-encrypted password. will be encrypted before being stored
     *
     * @param password un-encrypted password
     */
    public void setPassword(String password) {
        setValue(encrypt(password));
    }

    /**
     * One key to rule them all.
     */
    private static final String CRYPT_KEY = "109.215.82.241.150.240.110.59";
    private static final String SEP = ".";


    /**
     * Utility decryption method
     *
     * @param str the encrypted String
     * @return the decrypted String
     */
    public static String decrypt(String str) {
        if(!str.startsWith(ENCODED)) {
            return str;
        }
        byte[] result = null;

        if (str == null || "".equals(str)) return str;

        try {
            Key key = getKey();

            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            result = cipher.doFinal(stringToBytes(str));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            log.severe(e.toString());
        } catch (BadPaddingException e) {
            e.printStackTrace();
            log.severe(e.toString());
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
            log.severe(e.toString());
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            log.severe(e.toString());
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            log.severe(e.toString());
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
            log.severe(e.toString());
        }
        return (result == null) ? null : new String(result);
    }


    /**
     * Utility encryption method.
     *
     * @param str the cleartext String
     * @return the encrypted String
     */
    public static String encrypt(String str) {
        if(str.startsWith(ENCODED)) {
            return str;
        }
        byte[] result = null;

        if (str == null || "".equals(str)) return str;

        try {
            Key key = getKey();

            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            result = cipher.doFinal(str.getBytes());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            log.severe(e.toString());
        } catch (BadPaddingException e) {
            e.printStackTrace();
            log.severe(e.toString());
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
            log.severe(e.toString());
        } catch (InvalidKeyException e) {
            e.printStackTrace();
            log.severe(e.toString());
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
            log.severe(e.toString());
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
            log.severe(e.toString());
        }
        return (result == null) ? null : (ENCODED + bytesToString(result));
    }


    /**
     * Return a String as an array of bytes
     *
     * @param str the String to encode as bytes
     * @return an array of the characters from the String as bytes
     */
    private static byte[] stringToBytes(String str) {

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        StringTokenizer st = new StringTokenizer(str, SEP, false);
        while (st.hasMoreTokens()) {
            os.write((byte) Integer.parseInt(st.nextToken()));
        }
        return os.toByteArray();
    }

    private static Key stringToKey(String s) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        byte[] keyBytes = stringToBytes(s);
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        return keyFactory.generateSecret(new DESKeySpec(keyBytes));
    }

    /**
     * Converts the CRYPT_KEY String into a Key object
     *
     * @return the Key to use for encryption and decryption
     */
    private static Key getKey() throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        return stringToKey(CRYPT_KEY);
    }


    /**
     * Converts a byte array to a String representation
     *
     * @param bytes the byte array to represent as a String
     * @return the String representaton of the bytes
     */
    private static String bytesToString(byte[] bytes) {

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < bytes.length; i++) {
            sb.append((0x00FF & bytes[i]));
            if (i + 1 < bytes.length) {
                sb.append(SEP);
            }
        }
        return sb.toString();
    }
}
