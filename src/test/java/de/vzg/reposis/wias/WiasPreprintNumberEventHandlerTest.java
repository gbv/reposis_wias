package de.vzg.reposis.wias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.jdom2.Element;
import org.junit.Before;
import org.junit.Test;
import org.mycore.backend.jpa.MCREntityManagerProvider;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRStoreTestCase;
import org.mycore.common.MCRTestConfiguration;
import org.mycore.common.MCRTestProperty;
import org.mycore.common.events.MCREvent;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.mods.MCRMODSWrapper;

import jakarta.persistence.EntityManager;

@MCRTestConfiguration(properties = {
    @MCRTestProperty(key = "MCR.Metadata.Type.mods", string = "true"),
    @MCRTestProperty(key = "MCR.MODS.Types", string = "mods"),
    @MCRTestProperty(key = "WIAS.PostProcessor.PreprintSeriesId", string = "test_mods_00000012")
})
public class WiasPreprintNumberEventHandlerTest extends MCRStoreTestCase {

    private static final String PREPRINT_SERIES = "test_mods_00000012";
    private static final String OTHER_SERIES = "test_mods_00000099";

    private WiasPreprintNumberEventHandler handler;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        handler = new WiasPreprintNumberEventHandler();
    }

    @Test
    public void testCreateSyncsPreprintNumber() {
        MCRObject obj = buildPreprint("test_mods_00001001", PREPRINT_SERIES, 42);
        handler.handleObjectCreated(event(), obj);

        WiasPreprintNumberEntity entity = findEntity("test_mods_00001001");
        assertNotNull("Entity should be stored after create", entity);
        assertEquals(42, entity.getPreprintNumber());
    }

    @Test
    public void testUpdateChangesPreprintNumber() {
        MCRObject obj = buildPreprint("test_mods_00001002", PREPRINT_SERIES, 10);
        handler.handleObjectCreated(event(), obj);

        MCRObject updated = buildPreprint("test_mods_00001002", PREPRINT_SERIES, 11);
        handler.handleObjectUpdated(event(), updated);

        WiasPreprintNumberEntity entity = findEntity("test_mods_00001002");
        assertNotNull(entity);
        assertEquals(11, entity.getPreprintNumber());
    }

    @Test
    public void testDeleteRemovesEntry() {
        MCRObject obj = buildPreprint("test_mods_00001003", PREPRINT_SERIES, 7);
        handler.handleObjectCreated(event(), obj);
        assertNotNull(findEntity("test_mods_00001003"));

        handler.handleObjectDeleted(event(), obj);
        assertNull("Entity should be removed after delete", findEntity("test_mods_00001003"));
    }

    @Test
    public void testUndoCreateRemovesEntry() {
        MCRObject obj = buildPreprint("test_mods_00001004", PREPRINT_SERIES, 5);
        handler.handleObjectCreated(event(), obj);
        assertNotNull(findEntity("test_mods_00001004"));

        handler.undoObjectCreated(event(), obj);
        assertNull("Entity should be removed after undo-create", findEntity("test_mods_00001004"));
    }

    @Test
    public void testNonMatchingSeriesIsIgnored() {
        MCRObject obj = buildPreprint("test_mods_00001005", OTHER_SERIES, 99);
        handler.handleObjectCreated(event(), obj);

        assertNull("No entity for non-matching series", findEntity("test_mods_00001005"));
    }

    @Test
    public void testNonNumericVolumeIsIgnored() {
        MCRObject obj = new MCRObject();
        obj.setId(MCRObjectID.getInstance("test_mods_00001006"));
        obj.setSchema("datamodel-mods.xsd");
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        Element ri = new Element("relatedItem", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "series")
            .setAttribute("href", PREPRINT_SERIES, MCRConstants.XLINK_NAMESPACE);
        Element detail = new Element("detail", MCRConstants.MODS_NAMESPACE).setAttribute("type", "volume");
        detail.addContent(new Element("number", MCRConstants.MODS_NAMESPACE).setText("not-a-number"));
        ri.addContent(new Element("part", MCRConstants.MODS_NAMESPACE).addContent(detail));
        mods.addContent(ri);
        new MCRMODSWrapper(obj).setMODS(mods);

        handler.handleObjectCreated(event(), obj);
        assertNull("Non-numeric volume should not create entity", findEntity("test_mods_00001006"));
    }

    @Test
    public void testUpdateWithoutVolumeRemovesExistingEntry() {
        // First create with volume
        MCRObject obj = buildPreprint("test_mods_00001007", PREPRINT_SERIES, 20);
        handler.handleObjectCreated(event(), obj);
        assertNotNull(findEntity("test_mods_00001007"));

        // Now update with no volume set
        MCRObject noVolume = new MCRObject();
        noVolume.setId(MCRObjectID.getInstance("test_mods_00001007"));
        noVolume.setSchema("datamodel-mods.xsd");
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(new Element("relatedItem", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "series")
            .setAttribute("href", PREPRINT_SERIES, MCRConstants.XLINK_NAMESPACE));
        new MCRMODSWrapper(noVolume).setMODS(mods);

        handler.handleObjectUpdated(event(), noVolume);
        assertNull("Entry should be removed when volume is absent", findEntity("test_mods_00001007"));
    }

    // --- helpers ---

    private MCRObject buildPreprint(String id, String seriesId, int volume) {
        MCRObject obj = new MCRObject();
        obj.setId(MCRObjectID.getInstance(id));
        obj.setSchema("datamodel-mods.xsd");
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        Element ri = new Element("relatedItem", MCRConstants.MODS_NAMESPACE)
            .setAttribute("type", "series")
            .setAttribute("href", seriesId, MCRConstants.XLINK_NAMESPACE);
        Element detail = new Element("detail", MCRConstants.MODS_NAMESPACE).setAttribute("type", "volume");
        detail.addContent(new Element("number", MCRConstants.MODS_NAMESPACE).setText(String.valueOf(volume)));
        ri.addContent(new Element("part", MCRConstants.MODS_NAMESPACE).addContent(detail));
        mods.addContent(ri);
        new MCRMODSWrapper(obj).setMODS(mods);
        return obj;
    }

    private WiasPreprintNumberEntity findEntity(String mcrId) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        return em.find(WiasPreprintNumberEntity.class, mcrId);
    }

    private MCREvent event() {
        return new MCREvent(MCREvent.ObjectType.OBJECT, MCREvent.EventType.CREATE);
    }

}
