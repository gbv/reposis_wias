package de.vzg.reposis.wias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.Before;
import org.junit.Test;
import org.mycore.backend.jpa.MCREntityManagerProvider;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRJPATestCase;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.MCRSystemUserInformation;
import org.mycore.common.MCRTestConfiguration;
import org.mycore.common.MCRTestProperty;

import jakarta.persistence.EntityManager;

/**
 * Tests {@link WiasPostProcessor#process(Document)}.
 *
 * <p>Since no XSL stylesheet is configured, {@code MCRPostProcessorXSL.process()} just clones the
 * document. The input must therefore already be structured like the final mycoreobject XML so that
 * {@code fixVolume} can find the relatedItem via its absolute XPath.
 */
@MCRTestConfiguration(properties = {
    @MCRTestProperty(key = "MCR.MODS.Types", string = "mods"),
    @MCRTestProperty(key = "WIAS.PostProcessor.PreprintSeriesId", string = "test_mods_00000012")
})
public class WiasPostProcessorTest extends MCRJPATestCase {

    private static final String PREPRINT_SERIES = "test_mods_00000012";
    private static final String OTHER_SERIES = "test_mods_00000099";

    private WiasPostProcessor processor;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        MCRSessionMgr.getCurrentSession()
            .setUserInformation(MCRSystemUserInformation.getSuperUserInstance());
        processor = new WiasPostProcessor();
    }

    @Test
    public void testVolumeMissingIsAssignedFromDB() throws Exception {
        // DB is empty → MAX returns null → next volume = 1
        Document doc = buildMycoreobjectDoc(PREPRINT_SERIES, null, true);
        Document result = processor.process(doc);

        String volume = extractVolume(result);
        assertEquals("Volume should be 1 when DB is empty", "1", volume);
    }

    @Test
    public void testVolumeAssignedAsMaxPlusOne() throws Exception {
        // Seed DB with an existing entry
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        em.persist(new WiasPreprintNumberEntity("test_mods_00000001", 41));

        Document doc = buildMycoreobjectDoc(PREPRINT_SERIES, null, true);
        Document result = processor.process(doc);

        String volume = extractVolume(result);
        assertEquals("Volume should be MAX+1 = 42", "42", volume);
    }

    @Test
    public void testExistingVolumeIsNotOverwritten() throws Exception {
        Document doc = buildMycoreobjectDoc(PREPRINT_SERIES, "3259", true);
        Document result = processor.process(doc);

        String volume = extractVolume(result);
        assertEquals("Existing volume must not be overwritten", "3259", volume);
    }

    @Test
    public void testNonMatchingSeriesIsNotModified() throws Exception {
        Document doc = buildMycoreobjectDoc(OTHER_SERIES, null, true);
        Document result = processor.process(doc);

        // No volume should be assigned since the series doesn't match
        String volume = extractVolume(result);
        assertEquals("Non-matching series should not receive a volume", null, volume);
    }

    @Test
    public void testVolumeNotAssignedWithoutGenerateAttribute() throws Exception {
        Document doc = buildMycoreobjectDoc(PREPRINT_SERIES, null, false);
        Document result = processor.process(doc);

        String volume = extractVolume(result);
        assertEquals("Volume should not be assigned without generate attribute", null, volume);
    }

    @Test
    public void testGenerateAttributeRemovedAfterProcessing() throws Exception {
        Document doc = buildMycoreobjectDoc(PREPRINT_SERIES, null, true);
        Document result = processor.process(doc);

        assertNull("@generate-volume must be removed from output",
            extractSeriesItem(result).getAttributeValue("generate-volume"));
    }

    @Test
    public void testGenerateAttributeRemovedEvenWhenVolumeExists() throws Exception {
        Document doc = buildMycoreobjectDoc(PREPRINT_SERIES, "3259", true);
        Document result = processor.process(doc);

        assertNull("@generate-volume must be removed even when volume already set",
            extractSeriesItem(result).getAttributeValue("generate-volume"));
    }

    @Test
    public void testVolumeNotAssignedWithoutRequiredRole() throws Exception {
        // Set a guest user (no editor/admin role)
        MCRSessionMgr.getCurrentSession()
            .setUserInformation(MCRSystemUserInformation.getGuestInstance());

        Document doc = buildMycoreobjectDoc(PREPRINT_SERIES, null, true);
        Document result = processor.process(doc);

        String volume = extractVolume(result);
        assertEquals("Volume should not be assigned without editor/admin role", null, volume);
    }

    @Test
    public void testVolumeStrippedFromPreprintSeriesWithoutRequiredRole() throws Exception {
        MCRSessionMgr.getCurrentSession()
            .setUserInformation(MCRSystemUserInformation.getGuestInstance());

        Document doc = buildMycoreobjectDoc(PREPRINT_SERIES, "999", false);
        Document result = processor.process(doc);

        String volume = extractVolume(result);
        assertNull("Volume must be removed for unauthorized user on preprint series", volume);
    }

    @Test
    public void testVolumeNotStrippedFromOtherSeriesWithoutRequiredRole() throws Exception {
        MCRSessionMgr.getCurrentSession()
            .setUserInformation(MCRSystemUserInformation.getGuestInstance());

        Document doc = buildMycoreobjectDoc(OTHER_SERIES, "42", false);
        Document result = processor.process(doc);

        String volume = extractVolume(result);
        assertEquals("Volume on non-preprint series must not be touched", "42", volume);
    }

    // --- builders ---

    /**
     * Builds a minimal mycoreobject Document with a series relatedItem, mirroring the structure
     * that {@code fixVolume}'s XPath expects:
     * {@code /mycoreobject/metadata/def.modsContainer/modsContainer/mods:mods/mods:relatedItem[@type='series']}.
     */
    private Document buildMycoreobjectDoc(String seriesId, String volume, boolean generate) {
        Element relatedItem = new Element("relatedItem", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "series")
            .setAttribute("href", seriesId, MCRConstants.XLINK_NAMESPACE);
        if (generate) {
            relatedItem.setAttribute("generate-volume", "true");
        }
        if (volume != null) {
            Element detail = new Element("detail", MCRConstants.MODS_NAMESPACE).setAttribute("type", "volume");
            detail.addContent(new Element("number", MCRConstants.MODS_NAMESPACE).setText(volume));
            relatedItem.addContent(new Element("part", MCRConstants.MODS_NAMESPACE).addContent(detail));
        }

        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE).addContent(relatedItem);
        Element modsContainer = new Element("modsContainer").addContent(mods);
        Element defModsContainer = new Element("def.modsContainer").addContent(modsContainer);
        Element metadata = new Element("metadata").addContent(defModsContainer);
        Element root = new Element("mycoreobject").addContent(metadata);
        return new Document(root);
    }

    private String extractVolume(Document doc) {
        Element root = doc.getRootElement();
        Element metadata = root.getChild("metadata");
        if (metadata == null) {
            return null;
        }
        Element defMods = metadata.getChild("def.modsContainer");
        if (defMods == null) {
            return null;
        }
        Element modsContainer = defMods.getChild("modsContainer");
        if (modsContainer == null) {
            return null;
        }
        Element mods = modsContainer.getChild("mods", MCRConstants.MODS_NAMESPACE);
        if (mods == null) {
            return null;
        }
        Element ri = mods.getChildren("relatedItem", MCRConstants.MODS_NAMESPACE).stream()
            .filter(e -> "series".equals(e.getAttributeValue("type")))
            .findFirst().orElse(null);
        if (ri == null) {
            return null;
        }
        Element part = ri.getChild("part", MCRConstants.MODS_NAMESPACE);
        if (part == null) {
            return null;
        }
        Element detail = part.getChildren("detail", MCRConstants.MODS_NAMESPACE).stream()
            .filter(e -> "volume".equals(e.getAttributeValue("type")))
            .findFirst().orElse(null);
        if (detail == null) {
            return null;
        }
        Element number = detail.getChild("number", MCRConstants.MODS_NAMESPACE);
        return number == null ? null : number.getTextTrim();
    }

    private Element extractSeriesItem(Document doc) {
        return doc.getRootElement()
            .getChild("metadata")
            .getChild("def.modsContainer")
            .getChild("modsContainer")
            .getChild("mods", MCRConstants.MODS_NAMESPACE)
            .getChildren("relatedItem", MCRConstants.MODS_NAMESPACE).stream()
            .filter(e -> "series".equals(e.getAttributeValue("type")))
            .findFirst().orElse(null);
    }
}
