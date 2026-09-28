<?xml version="1.0" encoding="UTF-8"?>
<!--
  Fixes the malformed authority URI "hhttps://..." of wias_research_group classifications that was written by the
  editor form. Entries that would become duplicates of an already existing correct entry are removed.

  Usage in the MyCoRe CLI:
    select objects with solr query objectType:mods in core main
    execute for selected xslt {x} with file resource:xsl/migration/mycoreobject-fix-research-group-uri.xsl
  Objects without the malformed URI are left untouched, because the command only saves objects that changed.
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:mods="http://www.loc.gov/mods/v3">

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()" />
    </xsl:copy>
  </xsl:template>

  <!-- drop malformed entries whose corrected value already exists in a sibling -->
  <xsl:template match="mods:classification[starts-with(@authorityURI, 'hhttps://archive.wias-berlin.de/')]">
    <xsl:variable name="fixedValueURI" select="substring(@valueURI, 2)" />
    <xsl:if test="not(../mods:classification[@valueURI = $fixedValueURI])
      and not(preceding-sibling::mods:classification[@valueURI = current()/@valueURI])">
      <xsl:copy>
        <xsl:apply-templates select="@*|node()" />
      </xsl:copy>
    </xsl:if>
  </xsl:template>

  <!-- remove the duplicated leading "h" -->
  <xsl:template match="mods:classification/@authorityURI[starts-with(., 'hhttps://archive.wias-berlin.de/')]
    | mods:classification/@valueURI[starts-with(., 'hhttps://archive.wias-berlin.de/')]">
    <xsl:attribute name="{name()}">
      <xsl:value-of select="substring(., 2)" />
    </xsl:attribute>
  </xsl:template>

</xsl:stylesheet>
