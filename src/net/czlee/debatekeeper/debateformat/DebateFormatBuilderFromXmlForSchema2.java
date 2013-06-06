/*
 * Copyright (C) 2013 Chuan-Zheng Lee
 *
 * This file is part of the Debatekeeper app, which is licensed under the
 * GNU General Public Licence version 3 (GPLv3).  You can redistribute
 * and/or modify it under the terms of the GPLv3, and you must not use
 * this file except in compliance with the GPLv3.
 *
 * This app is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public Licence for more details.
 *
 * You should have received a copy of the GNU General Public Licence
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.czlee.debatekeeper.debateformat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.czlee.debatekeeper.R;
import net.czlee.debatekeeper.debateformat.DebateFormat.NoSuchFormatException;
import net.czlee.debatekeeper.debateformat.PeriodInfoManager.PeriodInfoException;
import net.czlee.debatekeeper.debateformat.XmlUtilities.XmlInvalidValueException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.content.Context;
import android.util.Log;

/**
 * DebateFormatBuilderForSchema2 provides mechanisms for building DebateFormats.
 *
 * <p>While "schema 2" refers to the XML schema, this class does not actually handle the XML.
 * It merely provides building methods for other classes to use to construct a {@link DebateFormat}.
 * The salient features of schema 2, that are not true for schema 1, are:</p>
 * <ul>
 * <li>"Resources" do not exist in schema 2 (they did in schema 1).</li>
 * <li>Most formats in schema 2 will just use the global period types; for schema 1 all
 * period types had to be defined locally.</li>
 * </ul>
 *
 * <p>It is expected that other classes will be used to interface between a means of storing
 * information about debate formats, and this class.  For example, the {@link DebateFormatBuilderFromXmlForSchema2}
 * class interfaces between XML files and this class.</p>
 *
 * <p>Because there is no longer any concept of "resources", there is nothing that a builder needs
 * to implement that isn't already taken care of by {@link DebateFormat}.  Therefore, unlike for
 * schema 1, this builder creates formats directly from XML files, without an intermediate class
 * to provide mechanisms for handling resources.  It does, however, have to handle global
 * period types, which it delegates to {@link PeriodInfoManager}.</p>
 *
 * <p>Unlike {@link DebateFormatBuilderForSchema1}, this class does not do error checking that is
 * handled by the schema.  It does do error checking that is not handled by the schema.</p>
 *
 * @author Chuan-Zheng Lee
 *
 */
public class DebateFormatBuilderFromXmlForSchema2 implements DebateFormatBuilderFromXml {

    private final DocumentBuilderFactory mDocumentBuilderFactory;
    private final PeriodInfoManager      mPeriodInfoManager;
    private final Context                mContext;
    private final ArrayList<String>      mErrorLog = new ArrayList<String>();

    private final XmlUtilities xu;

    private String mSchemaVersion;
    private static final String MINIMUM_SCHEMA_VERSION = "2.0";
    private static final String MAXIMUM_SCHEMA_VERSION = "2.0";

    /**
     * Constructor.
     */
    public DebateFormatBuilderFromXmlForSchema2(Context context) {
        super();
        mContext = context;
        mDocumentBuilderFactory = DocumentBuilderFactory.newInstance();
        mPeriodInfoManager = new PeriodInfoManager(context);
        xu = new XmlUtilities(context.getResources());

    }

    //******************************************************************************************
    // Public methods
    //******************************************************************************************
    /**
     * @param is an {@link InputStream} being the XML file to parse into a {@link DebateFormat}
     * @return the completed {@link DebateFormat}
     * @throws IOException if there was an IO error with the <code>InputStream</code>
     * @throws SAXException if thrown by the XML parser
     */
    @Override
    public DebateFormat buildDebateFromXml(InputStream is)
            throws SAXException, IOException {

        DebateFormat df = new DebateFormat();
        Document doc = getDocumentFromInputStream(is);
        Element root = doc.getDocumentElement();

        // 0. Schema version
        mSchemaVersion = xu.findAttributeText(root, R.string.xml2attrName_root_schemaVersion);

        if (mSchemaVersion == null)
            logXmlError(R.string.xmlError_rootNoSchemaVersion);
        else {
            try {
                if (!isSchemaSupported())
                    logXmlError(R.string.xmlError_rootNewSchemaVersion, mSchemaVersion, MAXIMUM_SCHEMA_VERSION);
            } catch (IllegalArgumentException e) {
                logXmlError(R.string.xmlError_rootInvalidSchemaVersion, mSchemaVersion);
            }
        }

        // 1. <name> - mandatory
        String name = xu.findElementText(root, R.string.xml2elemName_name);

        if (name == null) {
            logXmlError(R.string.xml2error_root_noName);
            name = "<not named>";
        }

        df.setName(name); // do this even if there was an error with the name

        // 2. If there are <period-type>s in this format, deal with them first.  We'll need to
        // store them somewhere useful in the meantime.
        Element periodTypes = xu.findElement(root, R.string.xml2elemName_periodTypes);
        if (periodTypes != null) {
            NodeList periodTypeElements = xu.findAllElements(periodTypes, R.string.xml2elemName_periodType);
            for (int i = 0; i < periodTypeElements.getLength(); i++) {
                Element periodType = (Element) periodTypeElements.item(i);
                try {
                    mPeriodInfoManager.addPeriodInfoFromElement(periodType);
                } catch (PeriodInfoException e) {
                    logXmlError(e);
                }
                // Check for and log non-fatal errors, if any
                Iterator<String> errors = mPeriodInfoManager.lastElementErrors().iterator();
                while (errors.hasNext()) logXmlError(errors.next());
            }
        }

        // 3. <prep-time> or <prep-time-controlled> - optional, only one allowed
        Element prepTimeSimple     = xu.findElement(root, R.string.xml2elemName_prepTimeSimpleFormat);
        Element prepTimeControlled = xu.findElement(root, R.string.xml2elemName_prepTimeControlledFormat);

        if (prepTimeSimple != null && prepTimeControlled != null) {
            logXmlError(R.string.xml2error_prepTime_multiple);
        } else if (prepTimeSimple != null) {
            PrepTimeSimpleFormat ptsf = createPrepTimeSimpleFormatFromElement(prepTimeSimple);
            if (ptsf != null) df.setPrepFormat(ptsf);
        } else if (prepTimeControlled != null) {
            PrepTimeControlledFormat ptcf = createPrepTimeControlledFormatFromElement(prepTimeControlled);
            if (ptcf != null) df.setPrepFormat(ptcf);
        }

        // 4. <speech-types>/<speech-type> (speech formats)
        Element speechFormats = xu.findElement(root, R.string.xml2elemName_speechFormats);
        if (speechFormats == null) return df; // we can't do anything if there aren't any speech formats, so just return

        NodeList speechFormatElements = xu.findAllElements(speechFormats, R.string.xml2elemName_speechFormat);
        for (int i = 0; i < speechFormatElements.getLength(); i++) {
            Element speechFormatElement = (Element) speechFormatElements.item(i);
            SpeechFormat sf = createSpeechFormatFromElement(speechFormatElement);
            if (sf == null) continue;
            String reference = sf.getReference();

            if (df.hasSpeechFormat(reference)) {
                logXmlError(R.string.dfb2error_speechFormat_duplicate, reference);
                continue;
            }

            df.addSpeechFormat(reference, sf);
        }

        // 5. <speeches>/<speech>
        Element speechesList = xu.findElement(root, R.string.xml2elemName_speechesList);
        if (speechesList == null) return df; // we can't do anything if there aren't any speeches, so just return

        NodeList speechElements = xu.findAllElements(speechesList, R.string.xml2elemName_speech);
        for (int i = 0; i < speechElements.getLength(); i++) {

            Element speechElement = (Element) speechElements.item(i);
            String speechName = xu.findElementText(speechElement, R.string.xml2elemName_speech_name);
            String formatRef = xu.findAttributeText(speechElement, R.string.xml2attrName_speech_format);

            if (speechName == null) {
                logXmlError(R.string.xml2error_speech_name_null);
                continue;
            }
            if (formatRef == null) {
                logXmlError(R.string.xml2error_speech_format_null, speechName);
                continue;
            }

            try {
                df.addSpeech(speechName, formatRef);
            } catch (NoSuchFormatException e) {
                logXmlError(R.string.dfb2error_addSpeech_speechFormatNotFound, formatRef, name);
                continue;
            }
        }

        return df;
    }

    @Override
    public boolean hasErrors() {
        return mErrorLog.size() > 0;
    }


    @Override
    public boolean isSchemaSupported() {
        if (mSchemaVersion == null)
            return false; // either not built, or if it was built then probably the wrong schema
        return (XmlUtilities.compareSchemaVersions(mSchemaVersion, MAXIMUM_SCHEMA_VERSION) <= 0)
                && (XmlUtilities.compareSchemaVersions(mSchemaVersion, MINIMUM_SCHEMA_VERSION) >= 0);
    }

    @Override
    public ArrayList<String> getErrorLog() {
        return mErrorLog;
    }

    //******************************************************************************************
    // Private methods
    //******************************************************************************************

    private Document getDocumentFromInputStream(InputStream is)
            throws SAXException, IOException {

        DocumentBuilder builder;
        try {
            // There should never be a problem creating this DocumentBuilder, ever.
            builder = mDocumentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
            Log.wtf(this.getClass().getSimpleName(), "Error creating document builder");
            // After this, the app is pretty much guaranteed to crash.
            return null;
        }

        // Parse the file
        return builder.parse(is);

    }

    /**
     * Creates a {@link SpeechFormat} derived from an {@link Element}
     * @param element an {@link Element} object
     * @return a {@link SpeechFormat}, may return <code>null</code> if there was an error preventing
     * the object from being created
     */
    private SpeechFormat createSpeechFormatFromElement(Element element) {

        // attribute 'ref' - mandatory
        String reference = xu.findAttributeText(element, R.string.xml2attrName_common_ref);

        if (reference == null) {
            logXmlError(R.string.xml2error_speechFormat_ref_null);
            return null;
        }
        if (reference.length() == 0) {
            logXmlError(R.string.xml2error_speechFormat_ref_blank);
            return null;
        }

        // attribute 'length' - mandatory
        Long length;
        try {
            length = xu.findAttributeAsTime(element, R.string.xml2attrName_controlledTimeLength);
        } catch (XmlInvalidValueException e) {
            logXmlError(R.string.xml2error_speechFormat_length_invalid, e.getValue(), reference);
            return null;
        }
        if (length == null) {
            logXmlError(R.string.xml2error_speechFormat_length_null, reference);
            return null;
        }

        SpeechFormat sf = new SpeechFormat(reference, length);

        String location = getString(R.string.xml2elemName_speechFormat) + "'" + reference + "'"; // for error messages

        populateControlledTimeFormat(sf, element, location);

        return sf;
    }

    /**
     * Creates a {@link BellInfo} derived from an {@link Element}
     * @param element an {@link Element} object
     * @param finishTime the length of the speech, used if the bell is to be at the finish
     * time of the speech
     * @return a {@link BellInfo}, may return <code>null</code> if there was an error preventing
     * the object from being created
     */
    private BellInfo createBellInfoFromElement(Element element, long finishTime, String location) {

        // attribute 'time' - mandatory
        long time;
        String timeStr = xu.findAttributeText(element, R.string.xml2attrName_bell_time);

        if (timeStr == null) {
            logXmlError(R.string.xml2error_bell_time_null);
            return null;
        }

        if (timeStr.equals(getString(R.string.xml2attrValue_bell_time_finish)))
            time = finishTime;
        else {
            try {
                time = XmlUtilities.timeStr2Secs(timeStr);
            } catch (NumberFormatException e) {
                logXmlError(R.string.xml2error_bell_time_invalid, timeStr, location);
                return null;
            }
        }

        if (time > finishTime) {
            logXmlError(R.string.dfb2error_bell_afterFinishTime, timeStr, location); // not checked by schema
            return null;
        }

        // attribute 'number' - optional, defaults to 1
        Integer timesToPlay;
        try {
            timesToPlay = xu.findAttributeAsInteger(element, R.string.xml2attrName_bell_number);
        } catch (XmlInvalidValueException e) {
            logXmlError(R.string.xml2error_bell_number_invalid, e.getValue(), location, timeStr);
            timesToPlay = 1;
        }
        if (timesToPlay == null) timesToPlay = 1;

        BellInfo bi = new BellInfo(time, timesToPlay);

        // attribute 'next-period' - optional
        // If there is a next period specified, and it is not "#stay", set it accordingly
        String nextPeriod = xu.findAttributeText(element, R.string.xml2attrName_bell_nextPeriod);
        if (nextPeriod != null) {
            if (!nextPeriod.equals(getString(R.string.xml1attrValue_common_stay))) {
                PeriodInfo npi = mPeriodInfoManager.getPeriodInfo(nextPeriod);
                if (npi == null)
                    logXmlError(R.string.dfb2error_periodInfo_notFound, nextPeriod);
                else
                    bi.setNextPeriodInfo(npi);
            }
        }

        // attribute 'pause-on-bell' - optional
        boolean pauseOnBell;
        try {
            pauseOnBell = xu.isAttributeTrue(element, R.string.xml2attrName_bell_pauseOnBell);
        } catch (XmlInvalidValueException e) {
            logXmlError(R.string.xml2error_bell_pauseOnBell_invalid, e.getValue(), location, timeStr);
            pauseOnBell = false;
        }
        bi.setPauseOnBell(pauseOnBell);

        return bi;

    }

    /**
     * Creates a {@link PrepTimeSimpleFormat} derived from an {@link Element}
     * @param element an {@link Element} object
     * @return a {@link PrepTimeSimpleFormat}, may return <code>null</code> if there was an error preventing
     * the object from being created
     */
    private PrepTimeSimpleFormat createPrepTimeSimpleFormatFromElement(Element element) {

        Long length;
        try {
            length = xu.findAttributeAsTime(element, R.string.xml2attrName_controlledTimeLength);
        } catch (XmlInvalidValueException e) {
            logXmlError(R.string.xml2error_prepTimeSimple_length_invalid, e.getValue());
            return null;
        }
        if (length == null) {
            logXmlError(R.string.xml2error_prepTimeSimple_length_null);
            return null;
        }

        return new PrepTimeSimpleFormat(length);

    }

    /**
     * Creates a {@link PrepTimeControlledFormat} derived from an {@link Element}
     * @param element an {@link Element} object
     * @return a {@link PrepTimeControlledFormat}, may return <code>null</code> if there was an error preventing
     * the object from being created
     */
    private PrepTimeControlledFormat createPrepTimeControlledFormatFromElement(Element element) {

        Long length;
        try {
            length = xu.findAttributeAsTime(element, R.string.xml2attrName_controlledTimeLength);
        } catch (XmlInvalidValueException e) {
            logXmlError(R.string.xml2error_prepTimeControlled_length_invalid, e.getValue());
            return null;
        }
        if (length == null) {
            logXmlError(R.string.xml2error_prepTimeControlled_length_null);
            return null;
        }

        PrepTimeControlledFormat ptcf = new PrepTimeControlledFormat(length);

        String location = getString(R.string.xml2elemName_prepTimeControlledFormat); // for error messages
        populateControlledTimeFormat(ptcf, element, location);

        return ptcf;
    }

    /**
     * Populates a {@link ControlledSpeechOrPrepFormat} with the first-period and the bells in
     * the {@link Element}.  By the time this method is called, the {@link ControlledSpeechOrPrepFormat}
     * (more likely one of its subclasses) must already exist and have a length associated with it.
     * This method exists mainly to avoid duplicate code to handle the two subclasses of
     * {@link ControlledSpeechOrPrepFormat} ({@link SpeechFormat} and {@link PrepTimeControlledFormat}).
     * @param cspf
     * @param element
     * @param length
     */
    private void populateControlledTimeFormat(ControlledSpeechOrPrepFormat cspf, Element element, String location) {

        // If there is a first period specified, and it is not "#stay", set it accordingly
        String firstPeriod = xu.findAttributeText(element, R.string.xml2attrName_controlledTimeFirstPeriod);
        if (firstPeriod != null) {
            if (!firstPeriod.equals(getString(R.string.xml1attrValue_common_stay))) {
                PeriodInfo npi = mPeriodInfoManager.getPeriodInfo(firstPeriod);
                if (npi == null)
                    logXmlError(R.string.dfb2error_periodInfo_notFound, firstPeriod); // not checked by schema
                else
                    cspf.setFirstPeriodInfo(npi);
            }
        }

        long length = cspf.getLength();

        // Add all the bells
        NodeList bellElements = xu.findAllElements(element, R.string.xml2elemName_bell);
        for (int i = 0; i < bellElements.getLength(); i++) {
            Element bellElement = (Element) bellElements.item(i);
            BellInfo bi = createBellInfoFromElement(bellElement, length, location);
            if (bi == null) continue;
            cspf.addBellInfo(bi);
        }

    }

    private String getString(int resId, Object... formatArgs) {
        return mContext.getString(resId, formatArgs);
    }

    // Error log methods

    /**
     * Logs an XML-related error from a string.
     * @param message the string
     */
    private void logXmlError(String message) {
        String bullet = "� ";
        String line   = bullet.concat(message);
        mErrorLog.add(line);
        Log.e("logXmlError", message);
    }

    /**
     * Logs an XML-related error from an exception.
     * @param e the Exception
     */
    private void logXmlError(Exception e) {
        logXmlError(e.getMessage());
    }

    /**
     * Logs an XML-related error from a string resource.
     * @param resId the resource ID of the string resource
     */
    private void logXmlError(int resId) {
        logXmlError(mContext.getString(resId));
    }

    /**
     * Logs an XML-related error from a string resource and formats according to
     * <code>String.format</code>
     * @param resId the resource ID of the string resource
     * @param formatArgs arguments to pass to <code>String.format</code>
     */
    private void logXmlError(int resId, Object... formatArgs) {
        logXmlError(mContext.getString(resId, formatArgs));
    }

}
