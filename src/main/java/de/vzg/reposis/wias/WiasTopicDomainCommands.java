package de.vzg.reposis.wias;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.filter.Filters;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRException;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.datamodel.classifications2.MCRCategLinkReference;
import org.mycore.datamodel.classifications2.MCRCategLinkService;
import org.mycore.datamodel.classifications2.MCRCategLinkServiceFactory;
import org.mycore.datamodel.classifications2.MCRCategory;
import org.mycore.datamodel.classifications2.MCRCategoryDAO;
import org.mycore.datamodel.classifications2.MCRCategoryDAOFactory;
import org.mycore.datamodel.classifications2.MCRCategoryID;
import org.mycore.datamodel.classifications2.utils.MCRCategoryTransformer;
import org.mycore.datamodel.classifications2.utils.MCRXMLTransformer;
import org.mycore.datamodel.common.MCRXMLMetadataManager;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.frontend.cli.annotation.MCRCommand;
import org.mycore.frontend.cli.annotation.MCRCommandGroup;

/**
 * Commands to convert the category IDs of the classification {@value #CLASSIFICATION_ID} to lowercase.
 *
 * <p>The rename runs in three steps:
 * <ol>
 * <li>The category links of all objects linked to a category that has to be renamed are deleted, so that the old
 * categories can be removed from the database without violating the foreign key of the link table.</li>
 * <li>The classification is replaced by a copy with lowercase category IDs.</li>
 * <li>For every MODS object the metadata XML is changed directly in the {@link MCRXMLMetadataManager} (without
 * firing events), including the copies of linked objects in mods:relatedItem. Every changed object is repaired
 * afterwards, which rebuilds the category links and the search index.</li>
 * </ol>
 */
@MCRCommandGroup(name = "WIAS Topic Domain Commands")
public class WiasTopicDomainCommands {

    static final String CLASSIFICATION_ID = "wias_topic_domain";

    static final String AUTHORITY_URI = "https://publications.wias-berlin.de/" + CLASSIFICATION_ID;

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Renames all categories of {@value #CLASSIFICATION_ID} whose ID is not lowercase and fixes all MODS objects.
     *
     * @return the sub commands fixing each MODS object
     */
    @MCRCommand(syntax = "wias lowercase topic domain ids",
        help = "Converts all category IDs of the classification wias_topic_domain to lowercase and fixes and repairs"
            + " all MODS objects containing a renamed category.",
        order = 20)
    public static List<String> lowercaseTopicDomainIds() throws URISyntaxException {
        MCRCategoryDAO dao = MCRCategoryDAOFactory.getInstance();
        MCRCategLinkService linkService = MCRCategLinkServiceFactory.getInstance();
        MCRCategory root = dao.getCategory(MCRCategoryID.rootID(CLASSIFICATION_ID), -1);
        if (root == null) {
            throw new MCRException("Classification " + CLASSIFICATION_ID + " does not exist.");
        }

        Map<String, String> renames = collectRenames(root);
        if (renames.isEmpty()) {
            LOGGER.info("All category IDs of {} are already lowercase.", CLASSIFICATION_ID);
            return List.of();
        }

        Set<String> objectIds = new TreeSet<>();
        renames.keySet().forEach(
            categId -> objectIds
                .addAll(linkService.getLinksFromCategory(new MCRCategoryID(CLASSIFICATION_ID, categId))));
        List<MCRCategLinkReference> references = objectIds.stream()
            .map(MCRObjectID::getInstance)
            .map(MCRCategLinkReference::new)
            .toList();
        LOGGER.info("Deleting category links of {} objects.", references.size());
        linkService.deleteLinks(references);

        Document classification = MCRCategoryTransformer.getMetaDataDocument(root, false);
        for (Element category : classification.getRootElement().getDescendants(Filters.element("category"))) {
            String newId = renames.get(category.getAttributeValue("ID"));
            if (newId != null) {
                LOGGER.info("Renaming category {} to {}.", category.getAttributeValue("ID"), newId);
                category.setAttribute("ID", newId);
            }
        }
        dao.replaceCategory(MCRXMLTransformer.getCategory(classification));

        return MCRConfiguration2.getOrThrow("MCR.MODS.Types", MCRConfiguration2::splitValue)
            .flatMap(type -> MCRXMLMetadataManager.instance().listIDsOfType(type).stream())
            .sorted()
            .map(objectId -> "wias lowercase topic domain ids in object " + objectId)
            .toList();
    }

    /**
     * Converts the topic domain category IDs in the metadata of the given object to lowercase. The XML is stored
     * directly in the {@link MCRXMLMetadataManager} without firing events, so a changed object is repaired
     * afterwards.
     *
     * @param id the ID of the object
     * @return the repair command if the object was changed, otherwise an empty list
     */
    @MCRCommand(syntax = "wias lowercase topic domain ids in object {0}",
        help = "Converts the wias_topic_domain category IDs in the metadata of object {0} to lowercase without"
            + " firing events and repairs the object if it was changed.",
        order = 10)
    public static List<String> lowercaseTopicDomainIdsInObject(String id) throws IOException, JDOMException {
        MCRObjectID objectId = MCRObjectID.getInstance(id);
        MCRXMLMetadataManager metadataManager = MCRXMLMetadataManager.instance();
        Document xml = metadataManager.retrieveXML(objectId);
        if (lowercaseTopicDomainIds(xml)) {
            metadataManager.update(objectId, xml, new Date());
            LOGGER.info("Converted topic domain IDs of {} to lowercase.", id);
            return List.of("repair metadata search of ID " + id);
        }
        LOGGER.info("Object {} has no topic domain IDs to convert.", id);
        return List.of();
    }

    /**
     * Maps every category ID of the given classification that is not lowercase to its lowercase variant.
     *
     * @throws MCRException if two category IDs only differ in case
     */
    static Map<String, String> collectRenames(MCRCategory root) {
        Map<String, String> renames = new TreeMap<>();
        Set<String> lowercaseIds = new HashSet<>();
        List<MCRCategory> pending = new ArrayList<>(root.getChildren());
        while (!pending.isEmpty()) {
            MCRCategory category = pending.remove(pending.size() - 1);
            pending.addAll(category.getChildren());
            String categId = category.getId().getId();
            String lowercaseId = categId.toLowerCase(Locale.ROOT);
            if (!lowercaseIds.add(lowercaseId)) {
                throw new MCRException("Category ID " + lowercaseId + " of " + CLASSIFICATION_ID
                    + " exists more than once when ignoring case. Merge the categories manually first.");
            }
            if (!categId.equals(lowercaseId)) {
                renames.put(categId, lowercaseId);
            }
        }
        return renames;
    }

    /**
     * Converts the category part of the {@code valueURI} of all topic domain classifications in the given MODS
     * document to lowercase and removes entries that become duplicates.
     *
     * @return {@code true} if the document was changed
     */
    static boolean lowercaseTopicDomainIds(Document xml) {
        List<Element> classifications = new ArrayList<>();
        for (Element classification : xml.getRootElement()
            .getDescendants(Filters.element("classification", MCRConstants.MODS_NAMESPACE))) {
            if (AUTHORITY_URI.equals(classification.getAttributeValue("authorityURI"))) {
                classifications.add(classification);
            }
        }

        boolean changed = false;
        Map<Element, Set<String>> seenPerParent = new IdentityHashMap<>();
        for (Element classification : classifications) {
            Attribute valueURI = classification.getAttribute("valueURI");
            if (valueURI == null) {
                continue;
            }
            String value = valueURI.getValue();
            int fragmentStart = value.indexOf('#') + 1;
            String lowercaseValue = value.substring(0, fragmentStart)
                + value.substring(fragmentStart).toLowerCase(Locale.ROOT);
            Set<String> seen = seenPerParent.computeIfAbsent(classification.getParentElement(), p -> new HashSet<>());
            if (!seen.add(lowercaseValue)) {
                classification.detach();
                changed = true;
            } else if (!value.equals(lowercaseValue)) {
                valueURI.setValue(lowercaseValue);
                changed = true;
            }
        }
        return changed;
    }
}
