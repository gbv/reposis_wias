package de.vzg.reposis.wias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.Test;
import org.mycore.common.MCRConstants;

/**
 * Tests {@link WiasTopicDomainCommands#lowercaseTopicDomainIds(Document)}.
 */
public class WiasTopicDomainCommandsTest {

    private static final String RESEARCH_GROUP_URI =
        "https://archive.wias-berlin.de/api/v2/classifications/wias_research_group";

    @Test
    public void lowercasesTopicIdsAndRemovesDuplicates() {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(classification(WiasTopicDomainCommands.AUTHORITY_URI, "AnaPDE"));
        mods.addContent(classification(WiasTopicDomainCommands.AUTHORITY_URI, "anapde"));
        mods.addContent(classification(WiasTopicDomainCommands.AUTHORITY_URI, "MF4ML"));
        mods.addContent(classification(RESEARCH_GROUP_URI, "RG1"));
        Document xml = new Document(new Element("mycoreobject").addContent(mods));

        assertTrue(WiasTopicDomainCommands.lowercaseTopicDomainIds(xml));

        List<String> valueURIs = mods.getChildren("classification", MCRConstants.MODS_NAMESPACE).stream()
            .map(e -> e.getAttributeValue("valueURI"))
            .toList();
        assertEquals(List.of(
            WiasTopicDomainCommands.AUTHORITY_URI + "#anapde",
            WiasTopicDomainCommands.AUTHORITY_URI + "#mf4ml",
            RESEARCH_GROUP_URI + "#RG1"), valueURIs);
    }

    @Test
    public void leavesLowercaseTopicIdsUnchanged() {
        Element mods = new Element("mods", MCRConstants.MODS_NAMESPACE);
        mods.addContent(classification(WiasTopicDomainCommands.AUTHORITY_URI, "anapde"));
        Document xml = new Document(new Element("mycoreobject").addContent(mods));

        assertFalse(WiasTopicDomainCommands.lowercaseTopicDomainIds(xml));
    }

    private static Element classification(String authorityURI, String categId) {
        return new Element("classification", MCRConstants.MODS_NAMESPACE)
            .setAttribute("authorityURI", authorityURI)
            .setAttribute("valueURI", authorityURI + "#" + categId);
    }
}
