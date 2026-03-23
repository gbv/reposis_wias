package de.vzg.reposis.wias;

import org.mycore.backend.jpa.MCREntityManagerProvider;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.events.MCREvent;
import org.mycore.common.events.MCREventHandlerBase;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.mods.MCRMODSWrapper;

import jakarta.persistence.EntityManager;

/**
 * Keeps the {@code WIAS_PREPRINT_NUMBER} table in sync with the preprint volume field
 * in MODS. On every create/update the volume number is read from the configured
 * preprint series relatedItem and stored (or removed if absent). On delete the row
 * is removed unconditionally.
 *
 * <p>Configuration:
 * <pre>
 * WIAS.PostProcessor.PreprintSeriesId=wias_mods_00000012
 * </pre>
 */
public class WiasPreprintNumberEventHandler extends MCREventHandlerBase {

    private static String getPreprintSeriesId() {
        return MCRConfiguration2.getString("WIAS.PostProcessor.PreprintSeriesId").orElse("");
    }

    private static final String VOLUME_XPATH_TEMPLATE =
        "mods:relatedItem[@type='series'][@xlink:href='%s']/mods:part/mods:detail[@type='volume']/mods:number";

    @Override
    protected void handleObjectCreated(MCREvent evt, MCRObject obj) {
        syncPreprintNumber(obj);
    }

    @Override
    protected void handleObjectUpdated(MCREvent evt, MCRObject obj) {
        syncPreprintNumber(obj);
    }

    @Override
    protected void handleObjectDeleted(MCREvent evt, MCRObject obj) {
        deleteEntry(obj.getId().toString());
    }

    @Override
    protected void undoObjectCreated(MCREvent evt, MCRObject obj) {
        deleteEntry(obj.getId().toString());
    }

    private void syncPreprintNumber(MCRObject obj) {
        String preprintSeriesId = getPreprintSeriesId();
        if (preprintSeriesId.isBlank() || !MCRMODSWrapper.isSupported(obj)) {
            return;
        }

        MCRMODSWrapper wrapper = new MCRMODSWrapper(obj);
        if (wrapper.getMODS() == null) {
            return;
        }

        String xpath = String.format(VOLUME_XPATH_TEMPLATE, preprintSeriesId);
        String volumeStr = wrapper.getElementValue(xpath);

        if (volumeStr == null || volumeStr.isBlank()) {
            deleteEntry(obj.getId().toString());
            return;
        }

        try {
            int volume = Integer.parseInt(volumeStr.strip());
            EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
            WiasPreprintNumberEntity entity = em.find(WiasPreprintNumberEntity.class, obj.getId().toString());
            if (entity == null) {
                em.persist(new WiasPreprintNumberEntity(obj.getId().toString(), volume));
            } else {
                entity.setPreprintNumber(volume);
            }
        } catch (NumberFormatException e) {
            // non-numeric volume – leave table untouched
        }
    }

    private void deleteEntry(String mcrId) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        WiasPreprintNumberEntity entity = em.find(WiasPreprintNumberEntity.class, mcrId);
        if (entity != null) {
            em.remove(entity);
        }
    }
}
