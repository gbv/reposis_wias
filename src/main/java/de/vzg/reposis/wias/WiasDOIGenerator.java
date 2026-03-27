package de.vzg.reposis.wias;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mycore.common.config.annotation.MCRPostConstruction;
import org.mycore.datamodel.metadata.MCRBase;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.mods.MCRMODSWrapper;
import org.mycore.pi.MCRPIGenerator;
import org.mycore.pi.MCRPIManager;
import org.mycore.pi.MCRPIRegistrationInfo;
import org.mycore.pi.doi.MCRDOIParser;
import org.mycore.pi.doi.MCRDigitalObjectIdentifier;
import org.mycore.pi.exceptions.MCRPersistentIdentifierException;

/**
 * Generates DOIs for WIAS publications based on genre and series membership.
 *
 * <p>DOI rules:
 * <ul>
 *   <li>Preprint (genre=preprint): {@code 10.20347/WIAS.PREPRINT.<Volume>} – volume from series relatedItem</li>
 *   <li>Research data (genre=research_data): {@code 10.20347/WIAS.DATA.<N>} – auto-incremented counter</li>
 *   <li>Software (genre=software): {@code 10.20347/WIAS.SOFTWARE.<N>} – auto-incremented counter per prefix</li>
 *   <li>Technical report (genre=report, TECHREPORT series): {@code 10.20347/WIAS.TECHREPORT.<N>} – auto-incremented counter</li>
 *   <li>Annual report (genre=report, ARR series): {@code 10.20347/WIAS.ARR.<YY>000}</li>
 *   <li>Article in annual report (genre=article, nested ARR series ref): {@code 10.20347/WIAS.ARR.<YY><NNN>} – year-based counter</li>
 * </ul>
 *
 * <p>Configuration:
 * <pre>
 * MCR.PI.Generator.DOIGenerator=de.vzg.reposis.wias.WiasDOIGenerator
 * MCR.PI.Generator.DOIGenerator.Prefix=10.20347
 * MCR.PI.Generator.DOIGenerator.AnnualReportSeriesId=wias_mods_00000034
 * MCR.PI.Generator.DOIGenerator.TechReportSeriesId=wias_mods_00000035
 * </pre>
 */
public class WiasDOIGenerator extends MCRPIGenerator<MCRDigitalObjectIdentifier> {

    private static final String GENRE_XPATH = "mods:genre[@type='intern']";

    private static final String SERIES_VOLUME_XPATH_TEMPLATE =
        "mods:relatedItem[@type='series'][@xlink:href='%s']/mods:part/mods:detail[@type='volume']/mods:number";

    private static final String DATE_ISSUED_XPATH = "mods:originInfo[@eventType='publication']/mods:dateIssued";

    // Searches at any nesting level for a relatedItem referencing the given ID
    private static final String ARR_SERIES_XPATH_TEMPLATE = ".//mods:relatedItem[@xlink:href='%s']";

    private static final Map<String, AtomicInteger> COUNTER_MAP = new HashMap<>();

    private String doiPrefix;

    private String preprintSeriesId;

    private String annualReportSeriesId;

    private String techReportSeriesId;

    private String reportSeriesId;

    @MCRPostConstruction
    public void init(String property) {
        super.init(property);
        Map<String, String> properties = getProperties();
        doiPrefix = properties.get("Prefix");
        preprintSeriesId = properties.getOrDefault("PreprintSeriesId", "");
        annualReportSeriesId = properties.getOrDefault("AnnualReportSeriesId", "");
        techReportSeriesId = properties.getOrDefault("TechReportSeriesId", "");
        reportSeriesId = properties.getOrDefault("ReportSeriesId", "");
    }

    @Override
    public MCRDigitalObjectIdentifier generate(MCRBase mcrBase, String additional)
        throws MCRPersistentIdentifierException {

        if (!(mcrBase instanceof MCRObject mcrObject)) {
            throw new MCRPersistentIdentifierException(
                "Only MCRObject instances are supported for DOI generation.");
        }

        MCRMODSWrapper wrapper = new MCRMODSWrapper(mcrObject);
        if (wrapper.getMODS() == null) {
            throw new MCRPersistentIdentifierException(
                "No MODS content found in object: " + mcrObject.getId());
        }

        String genre = getGenre(wrapper);
        String doiString = buildDOI(wrapper, genre);

        return new MCRDOIParser().parse(doiString)
            .orElseThrow(
                () -> new MCRPersistentIdentifierException("Could not parse generated DOI: " + doiString));
    }

    private String buildDOI(MCRMODSWrapper wrapper, String genre) throws MCRPersistentIdentifierException {
        return switch (genre) {
            case "preprint" -> doiPrefix + "/WIAS.PREPRINT." + getSeriesVolume(wrapper, preprintSeriesId);
            case "research_data" -> doiPrefix + "/WIAS.DATA." + getNextCounter(counterPattern("WIAS\\.DATA"));
            case "software" -> doiPrefix + "/WIAS.SOFTWARE." + getNextCounter(counterPattern("WIAS\\.SOFTWARE"));
            case "report" -> buildReportDOI(wrapper);
            case "article", "chapter" -> buildAnnualReportArticleDOI(wrapper, genre);
            default -> throw new MCRPersistentIdentifierException("No DOI rule configured for genre: " + genre);
        };
    }

    private String counterPattern(String suffix) {
        return "(?i)" + Pattern.quote(doiPrefix) + "/" + suffix + "\\.(\\d+)";
    }

    /**
     * A report is either the annual report itself (linked to ARR series → WIAS.ARR.YY000),
     * a technical report (linked to the TECHREPORT series → WIAS.TECHREPORT.N with counter),
     * or a generic report (→ WIAS.REPORT.Volume).
     */
    private String buildReportDOI(MCRMODSWrapper wrapper) throws MCRPersistentIdentifierException {
        if (!annualReportSeriesId.isBlank() && referencesAnnualReportSeries(wrapper)) {
            return doiPrefix + "/WIAS.ARR." + getTwoDigitYear(wrapper) + "000";
        }
        if (!techReportSeriesId.isBlank() && referencesTechReportSeries(wrapper)) {
            return doiPrefix + "/WIAS.TECHREPORT." + getNextCounter(counterPattern("WIAS\\.TECHREPORT"));
        }
        if (!reportSeriesId.isBlank() && referencesReportSeries(wrapper)) {
            return doiPrefix + "/WIAS.REPORT." + getSeriesVolume(wrapper, reportSeriesId);
        }
        throw new MCRPersistentIdentifierException(
            "Report does not reference a known series (ARR, TechReport, or Report)");
    }

    private boolean referencesTechReportSeries(MCRMODSWrapper wrapper) {
        String xpath = String.format("mods:relatedItem[@type='series'][@xlink:href='%s']", techReportSeriesId);
        return wrapper.getElement(xpath) != null;
    }

    private boolean referencesReportSeries(MCRMODSWrapper wrapper) {
        String xpath = String.format("mods:relatedItem[@type='series'][@xlink:href='%s']", reportSeriesId);
        return wrapper.getElement(xpath) != null;
    }

    /**
     * An article belongs to the annual report when its host relatedItem (the concrete annual
     * report object) itself references the ARR series. The article number is a year-based counter.
     * Counter starts at 001 (000 is reserved for the annual report itself).
     */
    private String buildAnnualReportArticleDOI(MCRMODSWrapper wrapper, String genre)
        throws MCRPersistentIdentifierException {
        if (!annualReportSeriesId.isBlank() && referencesAnnualReportSeries(wrapper)) {
            String year = getTwoDigitYear(wrapper);
            // Pattern captures all ARR numbers for this year (including 000 for the report itself)
            String counterPattern = "(?i)" + Pattern.quote(doiPrefix) + "/WIAS\\.ARR\\." + year + "(\\d{3})";
            int num = getNextCounter(counterPattern);
            return doiPrefix + "/WIAS.ARR." + year + String.format("%03d", num);
        }
        throw new MCRPersistentIdentifierException(
            "No DOI rule configured for genre '" + genre + "': "
                + "only articles within the annual report series are supported");
    }

    private String getGenre(MCRMODSWrapper wrapper) throws MCRPersistentIdentifierException {
        var genreElement = wrapper.getElement(GENRE_XPATH);
        if (genreElement == null) {
            throw new MCRPersistentIdentifierException("No intern genre found in MODS record");
        }
        String valueURI = genreElement.getAttributeValue("valueURI");
        if (valueURI == null || !valueURI.contains("#")) {
            throw new MCRPersistentIdentifierException("Invalid or missing genre valueURI: " + valueURI);
        }
        return valueURI.substring(valueURI.lastIndexOf('#') + 1);
    }

    /**
     * Returns true if the MODS document contains a relatedItem referencing the annual report series
     * at any nesting level (direct for the annual report itself, nested for its articles).
     */
    private boolean referencesAnnualReportSeries(MCRMODSWrapper wrapper) {
        String xpath = String.format(ARR_SERIES_XPATH_TEMPLATE, annualReportSeriesId);
        return wrapper.getElement(xpath) != null;
    }

    private String getSeriesVolume(MCRMODSWrapper wrapper, String seriesId)
        throws MCRPersistentIdentifierException {
        if (seriesId.isBlank()) {
            throw new MCRPersistentIdentifierException("No series ID configured for volume lookup");
        }
        String xpath = String.format(SERIES_VOLUME_XPATH_TEMPLATE, seriesId);
        String volume = wrapper.getElementValue(xpath);
        if (volume == null || volume.isBlank()) {
            throw new MCRPersistentIdentifierException(
                "No volume found in series relatedItem for series: " + seriesId);
        }
        return volume.strip();
    }

    private String getTwoDigitYear(MCRMODSWrapper wrapper) throws MCRPersistentIdentifierException {
        String dateIssued = wrapper.getElementValue(DATE_ISSUED_XPATH);
        if (dateIssued == null || dateIssued.isBlank()) {
            throw new MCRPersistentIdentifierException("No dateIssued (eventType=publication) found");
        }
        String year = dateIssued.strip();
        if (year.length() >= 4) {
            return year.substring(2, 4);
        }
        if (year.length() == 2) {
            return year;
        }
        throw new MCRPersistentIdentifierException(
            "Cannot extract two-digit year from dateIssued: " + dateIssued);
    }

    private synchronized int getNextCounter(String pattern) {
        return COUNTER_MAP.computeIfAbsent(pattern, this::readCounterFromDatabase).getAndIncrement();
    }

    private AtomicInteger readCounterFromDatabase(String pattern) {
        Pattern regExpPattern = Pattern.compile(pattern);
        List<MCRPIRegistrationInfo> list = MCRPIManager.getInstance()
            .getList(MCRDigitalObjectIdentifier.TYPE, -1, -1);

        int maxCount = list.stream()
            .map(MCRPIRegistrationInfo::getIdentifier)
            .filter(regExpPattern.asPredicate())
            .map(pi -> {
                Matcher matcher = regExpPattern.matcher(pi);
                return (matcher.find() && matcher.groupCount() == 1)
                    ? Integer.parseInt(matcher.group(1))
                    : null;
            })
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(0);

        return new AtomicInteger(maxCount + 1);
    }
}
