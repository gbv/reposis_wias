package de.vzg.reposis.wias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Field;
import java.util.Map;

import org.jdom2.Element;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRStoreTestCase;
import org.mycore.common.MCRTestConfiguration;
import org.mycore.common.MCRTestProperty;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.mods.MCRMODSWrapper;
import org.mycore.pi.exceptions.MCRPersistentIdentifierException;

@MCRTestConfiguration(properties = {
    @MCRTestProperty(key = "MCR.Metadata.Type.mods", string = "true"),
    @MCRTestProperty(key = "MCR.MODS.Types", string = "mods")
})
public class WiasDOIGeneratorTest extends MCRStoreTestCase {

    private static final String PREFIX = "10.20347";
    private static final String PREPRINT_SERIES = "test_mods_00000012";
    private static final String ARR_SERIES = "test_mods_00000034";

    private WiasDOIGenerator generator;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        generator = new WiasDOIGenerator();
        generator.setProperties(Map.of(
            "Prefix", PREFIX,
            "PreprintSeriesId", PREPRINT_SERIES,
            "AnnualReportSeriesId", ARR_SERIES));
        generator.init("MCR.PI.Generator.DOIGenerator");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        clearCounterMap();
        super.tearDown();
    }

    @Test
    public void testPreprintDOI() throws MCRPersistentIdentifierException {
        MCRObject obj = buildObject("test_mods_00001111", buildPreprintMods(PREPRINT_SERIES, "3259", "2026"));
        String doi = generator.generate(obj, "").asString();
        assertEquals(PREFIX + "/WIAS.PREPRINT.3259", doi);
    }

    @Test
    public void testPreprintDOIWrongSeriesFails() {
        MCRObject obj = buildObject("test_mods_00001111", buildPreprintMods("test_mods_00000099", "3259", "2026"));
        assertThrows(MCRPersistentIdentifierException.class, () -> generator.generate(obj, ""));
    }

    @Test
    public void testPreprintMissingVolumeFails() {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("preprint"));
        Element ri = relatedItem("series", PREPRINT_SERIES);
        mods.addContent(ri);
        MCRObject obj = buildObject("test_mods_00001112", mods);
        assertThrows(MCRPersistentIdentifierException.class, () -> generator.generate(obj, ""));
    }

    @Test
    public void testResearchDataDOI() throws MCRPersistentIdentifierException {
        MCRObject obj = buildObject("test_mods_00003333", buildGenreMods("research_data"));
        String doi = generator.generate(obj, "").asString();
        assertEquals(PREFIX + "/WIAS.DATA.1", doi);
    }

    @Test
    public void testSoftwareDOI() throws MCRPersistentIdentifierException {
        MCRObject obj = buildObject("test_mods_00004444", buildGenreMods("software"));
        String doi = generator.generate(obj, "").asString();
        assertEquals(PREFIX + "/WIAS.SOFTWARE.1", doi);
    }

    @Test
    public void testCounterIncrementsAcrossCallsInSameTest() throws MCRPersistentIdentifierException {
        MCRObject obj1 = buildObject("test_mods_00003001", buildGenreMods("research_data"));
        MCRObject obj2 = buildObject("test_mods_00003002", buildGenreMods("research_data"));
        String doi1 = generator.generate(obj1, "").asString();
        String doi2 = generator.generate(obj2, "").asString();
        assertEquals(PREFIX + "/WIAS.DATA.1", doi1);
        assertEquals(PREFIX + "/WIAS.DATA.2", doi2);
    }

    @Test
    public void testReportDOI() throws MCRPersistentIdentifierException {
        MCRObject obj = buildObject("test_mods_00002222", buildReportMods("27"));
        String doi = generator.generate(obj, "").asString();
        assertEquals(PREFIX + "/WIAS.REPORT.27", doi);
    }

    @Test
    public void testAnnualReportDOI() throws MCRPersistentIdentifierException {
        MCRObject obj = buildObject("test_mods_00005555", buildAnnualReportMods(ARR_SERIES, "2026"));
        String doi = generator.generate(obj, "").asString();
        assertEquals(PREFIX + "/WIAS.ARR.26000", doi);
    }

    @Test
    public void testAnnualReportArticleDOI() throws MCRPersistentIdentifierException {
        MCRObject obj = buildObject("test_mods_00006666", buildAnnualReportArticleMods(ARR_SERIES, "2026"));
        String doi = generator.generate(obj, "").asString();
        assertEquals(PREFIX + "/WIAS.ARR.26001", doi);
    }

    @Test
    public void testUnknownGenreFails() {
        MCRObject obj = buildObject("test_mods_00007777", buildGenreMods("unknown_genre"));
        assertThrows(MCRPersistentIdentifierException.class, () -> generator.generate(obj, ""));
    }

    @Test
    public void testNoModsFails() {
        MCRObject obj = new MCRObject();
        obj.setId(MCRObjectID.getInstance("test_mods_00008888"));
        obj.setSchema("datamodel-mods.xsd");
        assertThrows(MCRPersistentIdentifierException.class, () -> generator.generate(obj, ""));
    }

    // --- MODS builders ---

    private Element buildPreprintMods(String seriesId, String volume, String year) {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("preprint"));
        Element ri = relatedItem("series", seriesId);
        ri.addContent(volumePart(volume));
        mods.addContent(ri);
        mods.addContent(dateIssued(year));
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
        Element ri = relatedItem("series", null);
        ri.addContent(volumePart(volume));
        mods.addContent(ri);
        return mods;
    }

    private Element buildAnnualReportMods(String arrSeriesId, String year) {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("report"));
        mods.addContent(relatedItem("series", arrSeriesId));
        mods.addContent(dateIssued(year));
        return mods;
    }

    private Element buildAnnualReportArticleMods(String arrSeriesId, String year) {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(genre("article"));
        Element host = relatedItem("host", null);
        host.addContent(relatedItem("series", arrSeriesId));
        mods.addContent(host);
        mods.addContent(dateIssued(year));
        return mods;
    }

    // --- XML helpers ---

    private Element genre(String value) {
        return new Element("genre", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "intern")
            .setAttribute("valueURI", "http://www.mycore.org/classifications/mir_genres#" + value);
    }

    private Element relatedItem(String type, String href) {
        Element ri = new Element("relatedItem", MCRConstants.MODS_NAMESPACE).setAttribute("type", type);
        if (href != null) {
            ri.setAttribute("href", href, MCRConstants.XLINK_NAMESPACE);
        }
        return ri;
    }

    private Element volumePart(String volume) {
        Element detail = new Element("detail", MCRConstants.MODS_NAMESPACE).setAttribute("type", "volume");
        detail.addContent(new Element("number", MCRConstants.MODS_NAMESPACE).setText(volume));
        Element part = new Element("part", MCRConstants.MODS_NAMESPACE);
        part.addContent(detail);
        return part;
    }

    private Element dateIssued(String year) {
        Element originInfo = new Element("originInfo", MCRConstants.MODS_NAMESPACE)
            .setAttribute("eventType", "publication");
        originInfo.addContent(new Element("dateIssued", MCRConstants.MODS_NAMESPACE).setText(year));
        return originInfo;
    }

    private MCRObject buildObject(String id, Element mods) {
        MCRObject obj = new MCRObject();
        obj.setId(MCRObjectID.getInstance(id));
        obj.setSchema("datamodel-mods.xsd");
        new MCRMODSWrapper(obj).setMODS(mods);
        return obj;
    }

    @SuppressWarnings("unchecked")
    private static void clearCounterMap() throws Exception {
        Field f = WiasDOIGenerator.class.getDeclaredField("COUNTER_MAP");
        f.setAccessible(true);
        ((java.util.Map<?, ?>) f.get(null)).clear();
    }
}
