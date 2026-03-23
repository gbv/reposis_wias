package de.vzg.reposis.wias;

import org.jdom2.Element;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.mir.authorization.MIRStrategy;
import org.mycore.mods.MCRMODSWrapper;

/**
 * Extends {@link MIRStrategy} to block DOI generation when the metadata conditions
 * required by {@link WiasDOIGenerator} are not met, without calling the generator itself.
 *
 * <p>The check is triggered for the permission {@code "register-<serviceId>"} where
 * {@code serviceId} is configured via:
 * <pre>
 * WIAS.Strategy.DOIServiceId=Datacite
 * </pre>
 *
 * <p>The following generator properties are also read:
 * <pre>
 * MCR.PI.Generator.DOIGenerator.PreprintSeriesId=wias_mods_00000012
 * MCR.PI.Generator.DOIGenerator.AnnualReportSeriesId=wias_mods_00000034
 * </pre>
 */
public class WiasStrategy extends MIRStrategy {

    private static String getDoiServiceId() {
        return MCRConfiguration2.getString("WIAS.Strategy.DOIServiceId").orElse("");
    }

    private static String getPreprintSeriesId() {
        return MCRConfiguration2.getString("MCR.PI.Generator.DOIGenerator.PreprintSeriesId").orElse("");
    }

    private static String getArrSeriesId() {
        return MCRConfiguration2.getString("MCR.PI.Generator.DOIGenerator.AnnualReportSeriesId").orElse("");
    }

    private static final String GENRE_XPATH = "mods:genre[@type='intern']";
    private static final String DATE_ISSUED_XPATH = "mods:originInfo[@eventType='publication']/mods:dateIssued";
    private static final String ARR_SERIES_XPATH_TEMPLATE = ".//mods:relatedItem[@xlink:href='%s']";

    @Override
    public boolean checkPermission(String id, String permission) {
        String doiServiceId = getDoiServiceId();
        if (!doiServiceId.isBlank()
            && ("register-" + doiServiceId).equals(permission)
            && MCRObjectID.isValid(id)
            && !canGenerateDOI(MCRObjectID.getInstance(id))) {
            return false;
        }
        return super.checkPermission(id, permission);
    }

    private boolean canGenerateDOI(MCRObjectID objectId) {
        try {
            MCRObject obj = MCRMetadataManager.retrieveMCRObject(objectId);
            MCRMODSWrapper wrapper = new MCRMODSWrapper(obj);
            if (wrapper.getMODS() == null) {
                return false;
            }
            String genre = getGenre(wrapper);
            if (genre == null) {
                return false;
            }
            return switch (genre) {
                case "preprint" -> hasVolumeInSeries(wrapper, getPreprintSeriesId());
                case "research_data", "software" -> true;
                case "report" -> referencesArrSeries(wrapper) ? hasDateIssued(wrapper)
                    : hasVolumeInSeries(wrapper, null);
                case "article", "chapter" -> referencesArrSeries(wrapper) && hasDateIssued(wrapper);
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    private String getGenre(MCRMODSWrapper wrapper) {
        Element genreElement = wrapper.getElement(GENRE_XPATH);
        if (genreElement == null) {
            return null;
        }
        String valueURI = genreElement.getAttributeValue("valueURI");
        if (valueURI == null || !valueURI.contains("#")) {
            return null;
        }
        return valueURI.substring(valueURI.lastIndexOf('#') + 1);
    }

    private boolean hasVolumeInSeries(MCRMODSWrapper wrapper, String seriesId) {
        String xpath = seriesId != null && !seriesId.isBlank()
            ? "mods:relatedItem[@type='series'][@xlink:href='" + seriesId
                + "']/mods:part/mods:detail[@type='volume']/mods:number"
            : "mods:relatedItem[@type='series']/mods:part/mods:detail[@type='volume']/mods:number";
        String volume = wrapper.getElementValue(xpath);
        return volume != null && !volume.isBlank();
    }

    private boolean referencesArrSeries(MCRMODSWrapper wrapper) {
        String arrSeriesId = getArrSeriesId();
        if (arrSeriesId.isBlank()) {
            return false;
        }
        String xpath = String.format(ARR_SERIES_XPATH_TEMPLATE, arrSeriesId);
        return wrapper.getElement(xpath) != null;
    }

    private boolean hasDateIssued(MCRMODSWrapper wrapper) {
        String date = wrapper.getElementValue(DATE_ISSUED_XPATH);
        return date != null && !date.isBlank();
    }
}
