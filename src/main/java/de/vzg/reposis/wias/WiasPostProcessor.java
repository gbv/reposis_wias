package de.vzg.reposis.wias;

import java.io.IOException;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.mycore.backend.jpa.MCREntityManagerProvider;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.mir.editor.MIRPostProcessor;

import jakarta.persistence.EntityManager;

/**
 * Extends {@link MIRPostProcessor} to automatically assign the next available volume number
 * to documents that reference the configured preprint series but have no volume set yet.
 *
 * <p>The next volume is determined by querying {@code MAX(preprintNumber)} from the
 * {@code WIAS_PREPRINT_NUMBER} table (maintained by {@link WiasPreprintNumberEventHandler})
 * and adding 1. Falls back to 1 if the table is empty.
 *
 * <p>Configuration:
 * <pre>
 * WIAS.PostProcessor.PreprintSeriesId=wias_mods_00000012
 * </pre>
 */
public class WiasPostProcessor extends MIRPostProcessor {

    private static String getPreprintSeriesId() {
        return MCRConfiguration2.getString("WIAS.PostProcessor.PreprintSeriesId").orElse("");
    }

    private static final XPathExpression<Element> SERIES_ITEM_XPATH = XPathFactory.instance().compile(
        "/mycoreobject/metadata/def.modsContainer/modsContainer/mods:mods/mods:relatedItem[@type='series']",
        Filters.element(), null, MCRConstants.MODS_NAMESPACE, MCRConstants.XLINK_NAMESPACE);

    private static final XPathExpression<Element> VOLUME_NUMBER_XPATH = XPathFactory.instance().compile(
        "mods:part/mods:detail[@type='volume']/mods:number",
        Filters.element(), null, MCRConstants.MODS_NAMESPACE);

    @Override
    public Document process(Document oldXML) throws IOException, JDOMException {
        Document result = super.process(oldXML);
        stripUnauthorizedVolume(result);
        fixVolume(result);
        removeGenerateAttributes(result);
        return result;
    }

    private void fixVolume(Document document) {
        Element seriesItem = SERIES_ITEM_XPATH.evaluateFirst(document);
        if (seriesItem == null) {
            return;
        }

        // Skip if volume is already set
        Element numberElement = VOLUME_NUMBER_XPATH.evaluateFirst(seriesItem);
        if (numberElement != null && !numberElement.getTextTrim().isBlank()) {
            return;
        }

        String seriesId = seriesItem.getAttributeValue("href", MCRConstants.XLINK_NAMESPACE);
        if (!getPreprintSeriesId().equals(seriesId)) {
            return;
        }

        if (!hasRequiredRole()) {
            return;
        }

        if (!hasGenerateAttribute(seriesItem)) {
            return;
        }

        int nextVolume = findNextVolume(seriesId);

        // Create part/detail/number structure if not present
        Element partElement = seriesItem.getChild("part", MCRConstants.MODS_NAMESPACE);
        if (partElement == null) {
            partElement = new Element("part", MCRConstants.MODS_NAMESPACE);
            seriesItem.addContent(partElement);
        }

        final Element part = partElement;
        Element detailElement = part.getChildren("detail", MCRConstants.MODS_NAMESPACE).stream()
            .filter(e -> "volume".equals(e.getAttributeValue("type")))
            .findFirst()
            .orElseGet(() -> {
                Element detail = new Element("detail", MCRConstants.MODS_NAMESPACE);
                detail.setAttribute("type", "volume");
                part.addContent(detail);
                return detail;
            });

        if (numberElement == null) {
            numberElement = new Element("number", MCRConstants.MODS_NAMESPACE);
            detailElement.addContent(numberElement);
        }

        numberElement.setText(String.valueOf(nextVolume));
    }

    /**
     * If the current user is not an editor or admin, removes any volume number
     * from the configured preprint series relatedItem. This prevents unauthorized
     * users from manually assigning a preprint volume.
     */
    private void stripUnauthorizedVolume(Document document) {
        if (hasRequiredRole()) {
            return;
        }
        Element seriesItem = SERIES_ITEM_XPATH.evaluateFirst(document);
        if (seriesItem == null) {
            return;
        }
        String seriesId = seriesItem.getAttributeValue("href", MCRConstants.XLINK_NAMESPACE);
        if (!getPreprintSeriesId().equals(seriesId)) {
            return;
        }
        Element numberElement = VOLUME_NUMBER_XPATH.evaluateFirst(seriesItem);
        if (numberElement != null) {
            numberElement.detach();
        }
    }

    /**
     * Removes the {@code @generate-volume} attribute from the series relatedItem,
     * since it is not valid MODS and only used as an editor form signal.
     */
    private void removeGenerateAttributes(Document document) {
        Element seriesItem = SERIES_ITEM_XPATH.evaluateFirst(document);
        if (seriesItem != null) {
            seriesItem.removeAttribute("generate-volume");
        }
    }

    private boolean hasRequiredRole() {
        var userInfo = MCRSessionMgr.getCurrentSession().getUserInformation();
        return userInfo.isUserInRole("editor") || userInfo.isUserInRole("admin");
    }

    private boolean hasGenerateAttribute(Element seriesItem) {
        return "true".equals(seriesItem.getAttributeValue("generate-volume"));
    }

    /**
     * Returns the next free volume number by querying MAX(preprintNumber) from the
     * WIAS_PREPRINT_NUMBER table. Returns 1 if the table is empty.
     */
    private int findNextVolume(String seriesId) {
        EntityManager em = MCREntityManagerProvider.getCurrentEntityManager();
        Integer max = (Integer) em
            .createQuery("SELECT MAX(e.preprintNumber) FROM WiasPreprintNumberEntity e")
            .getSingleResult();
        return max == null ? 1 : max + 1;
    }
}
