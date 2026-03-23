package de.vzg.reposis.wias;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;

import org.jdom2.Element;
import org.junit.Before;
import org.junit.Test;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRStoreTestCase;
import org.mycore.common.MCRSystemUserInformation;
import org.mycore.common.MCRTestConfiguration;
import org.mycore.common.MCRTestProperty;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.mods.MCRMODSWrapper;
import org.mycore.common.MCRSessionMgr;

/**
 * Tests the WiasStrategy WIAS-specific DOI permission logic via reflection on
 * {@code canGenerateDOI(MCRObjectID)}, isolating it from the MIR access-control stack.
 *
 * <p>Note: WiasStrategy has static config fields. They are initialized the first time the class
 * is loaded, which happens here after {@code @MCRTestConfiguration} properties are applied in
 * {@code setUp()}.
 */
@MCRTestConfiguration(properties = {
    @MCRTestProperty(key = "MCR.Metadata.Type.mods", string = "true"),
    @MCRTestProperty(key = "MCR.MODS.Types", string = "mods"),
    @MCRTestProperty(key = "WIAS.Strategy.DOIServiceId", string = "Datacite"),
    @MCRTestProperty(key = "MCR.PI.Generator.DOIGenerator.PreprintSeriesId", string = "test_mods_00000012"),
    @MCRTestProperty(key = "MCR.PI.Generator.DOIGenerator.AnnualReportSeriesId", string = "test_mods_00000034")
})
public class WiasStrategyTest extends MCRStoreTestCase {

    private static final String PREPRINT_SERIES = "test_mods_00000012";
    private static final String ARR_SERIES = "test_mods_00000034";

    private WiasStrategy strategy;
    private Method canGenerateDOI;

    @Override
    protected Map<String, String> getTestProperties() {
        Map<String, String> props = super.getTestProperties();
        props.put("MCR.Metadata.ShareAgent.mods", "");
        props.put("MCR.EventHandler.MCRObject.013.Class", "");
        props.put("MCR.EventHandler.MCRObject.015.Class", "");
        props.put("MCR.EventHandler.MCRObject.025.Class", "");
        props.put("MCR.EventHandler.MCRObject.040.Class", "");
        return props;
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        MCRSessionMgr.getCurrentSession()
            .setUserInformation(MCRSystemUserInformation.getSuperUserInstance());
        strategy = new WiasStrategy();
        canGenerateDOI = WiasStrategy.class.getDeclaredMethod("canGenerateDOI", MCRObjectID.class);
        canGenerateDOI.setAccessible(true);
    }

    @Test
    public void testPreprintWithVolumeDOIAllowed() throws Exception {
        MCRObjectID id = store("test_mods_00001001", buildPreprintMods(PREPRINT_SERIES, "3259"));
        assertTrue(invoke(id));
    }

    @Test
    public void testPreprintWithoutVolumeDOIDenied() throws Exception {
        // relatedItem present but no volume number
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("preprint"));
        mods.addContent(new Element("relatedItem", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "series")
            .setAttribute("href", PREPRINT_SERIES, MCRConstants.XLINK_NAMESPACE));
        MCRObjectID id = store("test_mods_00001002", mods);
        assertFalse(invoke(id));
    }

    @Test
    public void testResearchDataDOIAlwaysAllowed() throws Exception {
        MCRObjectID id = store("test_mods_00003001", buildGenreMods("research_data"));
        assertTrue(invoke(id));
    }

    @Test
    public void testSoftwareDOIAlwaysAllowed() throws Exception {
        MCRObjectID id = store("test_mods_00004001", buildGenreMods("software"));
        assertTrue(invoke(id));
    }

    @Test
    public void testReportWithVolumeDOIAllowed() throws Exception {
        MCRObjectID id = store("test_mods_00002001", buildReportMods("27"));
        assertTrue(invoke(id));
    }

    @Test
    public void testReportWithoutVolumeDOIDenied() throws Exception {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("report"));
        mods.addContent(new Element("relatedItem", MCRConstants.MODS_NAMESPACE).setAttribute("type", "series"));
        MCRObjectID id = store("test_mods_00002002", mods);
        assertFalse(invoke(id));
    }

    @Test
    public void testAnnualReportWithDateDOIAllowed() throws Exception {
        MCRObjectID id = store("test_mods_00005001", buildAnnualReportMods(ARR_SERIES, "2026"));
        assertTrue(invoke(id));
    }

    @Test
    public void testAnnualReportArticleWithDateDOIAllowed() throws Exception {
        MCRObjectID id = store("test_mods_00006001", buildArticleMods(ARR_SERIES, "2026"));
        assertTrue(invoke(id));
    }

    @Test
    public void testAnnualReportArticleWithoutDateDOIDenied() throws Exception {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("article"));
        Element host = new Element("relatedItem", MCRConstants.MODS_NAMESPACE).setAttribute("type", "host");
        host.addContent(new Element("relatedItem", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "series")
            .setAttribute("href", ARR_SERIES, MCRConstants.XLINK_NAMESPACE));
        mods.addContent(host);
        MCRObjectID id = store("test_mods_00006002", mods);
        assertFalse(invoke(id));
    }

    @Test
    public void testUnknownGenreDOIDenied() throws Exception {
        MCRObjectID id = store("test_mods_00007001", buildGenreMods("something_unknown"));
        assertFalse(invoke(id));
    }

    @Test
    public void testObjectWithoutModsDOIDenied() throws Exception {
        MCRObject obj = new MCRObject();
        MCRObjectID id = MCRObjectID.getInstance("test_mods_00008001");
        obj.setId(id);
        obj.setSchema("datamodel-mods.xsd");
        MCRMetadataManager.create(obj);
        assertFalse(invoke(id));
    }

    // --- helpers ---

    private MCRObjectID store(String id, Element mods) throws Exception {
        MCRObjectID objId = MCRObjectID.getInstance(id);
        MCRObject obj = new MCRObject();
        obj.setId(objId);
        obj.setSchema("datamodel-mods.xsd");
        new MCRMODSWrapper(obj).setMODS(mods);
        MCRMetadataManager.create(obj);
        return objId;
    }

    private boolean invoke(MCRObjectID id) throws Exception {
        return (boolean) canGenerateDOI.invoke(strategy, id);
    }

    private Element buildPreprintMods(String seriesId, String volume) {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("preprint"));
        Element ri = new Element("relatedItem", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "series")
            .setAttribute("href", seriesId, MCRConstants.XLINK_NAMESPACE);
        Element detail = new Element("detail", MCRConstants.MODS_NAMESPACE).setAttribute("type", "volume");
        detail.addContent(new Element("number", MCRConstants.MODS_NAMESPACE).setText(volume));
        ri.addContent(new Element("part", MCRConstants.MODS_NAMESPACE).addContent(detail));
        mods.addContent(ri);
        return mods;
    }

    private Element buildGenreMods(String genreValue) {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre(genreValue));
        return mods;
    }

    private Element buildReportMods(String volume) {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("report"));
        Element ri = new Element("relatedItem", MCRConstants.MODS_NAMESPACE).setAttribute("type", "series");
        Element detail = new Element("detail", MCRConstants.MODS_NAMESPACE).setAttribute("type", "volume");
        detail.addContent(new Element("number", MCRConstants.MODS_NAMESPACE).setText(volume));
        ri.addContent(new Element("part", MCRConstants.MODS_NAMESPACE).addContent(detail));
        mods.addContent(ri);
        return mods;
    }

    private Element buildAnnualReportMods(String arrSeriesId, String year) {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("report"));
        mods.addContent(new Element("relatedItem", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "series")
            .setAttribute("href", arrSeriesId, MCRConstants.XLINK_NAMESPACE));
        mods.addContent(dateIssued(year));
        return mods;
    }

    private Element buildArticleMods(String arrSeriesId, String year) {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("article"));
        Element host = new Element("relatedItem", MCRConstants.MODS_NAMESPACE).setAttribute("type", "host");
        host.addContent(new Element("relatedItem", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "series")
            .setAttribute("href", arrSeriesId, MCRConstants.XLINK_NAMESPACE));
        mods.addContent(host);
        mods.addContent(dateIssued(year));
        return mods;
    }

    private Element genre(String value) {
        return new Element("genre", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "intern")
            .setAttribute("valueURI", "http://www.mycore.org/classifications/mir_genres#" + value);
    }

    private Element dateIssued(String year) {
        return new Element("originInfo", MCRConstants.MODS_NAMESPACE)
            .setAttribute("eventType", "publication")
            .addContent(new Element("dateIssued", MCRConstants.MODS_NAMESPACE).setText(year));
    }
}
