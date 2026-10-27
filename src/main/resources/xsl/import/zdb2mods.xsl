<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:mods="http://www.loc.gov/mods/v3"
  xmlns:xalan="http://xml.apache.org/xalan"
  xmlns:java="http://xml.apache.org/xalan/java"
  xmlns:srw="http://www.loc.gov/zing/srw/"
  xmlns:marc="http://www.loc.gov/MARC21/slim"
  exclude-result-prefixes="xsl xalan java srw marc">

  <xsl:template match="srw:searchRetrieveResponse">
    <xsl:apply-templates select="srw:records/srw:record[1]/srw:recordData/marc:record" />
  </xsl:template>

  <xsl:template match="marc:record">
    <mods:mods>
      <mods:genre type="intern" authorityURI="http://www.mycore.org/classifications/mir_genres" valueURI="http://www.mycore.org/classifications/mir_genres#journal" />
      <xsl:apply-templates select="marc:datafield[@tag='245']" />
      <xsl:apply-templates select="marc:datafield[@tag='210']" />
      <xsl:apply-templates select="marc:datafield[@tag='264']" />
      <xsl:apply-templates select="marc:datafield[@tag='022']" />
      <xsl:apply-templates select="marc:datafield[@tag='041']" />
      <xsl:apply-templates select="marc:datafield[@tag='082']" />
      <xsl:apply-templates select="marc:datafield[@tag='856'][1]" />
    </mods:mods>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='245']">
    <mods:titleInfo>
      <mods:title>
        <xsl:value-of select="marc:subfield[@code='a']" />
      </mods:title>
    </mods:titleInfo>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='210']">
    <mods:titleInfo type="abbreviated">
      <mods:title>
        <xsl:value-of select="marc:subfield[@code='a']" />
      </mods:title>
    </mods:titleInfo>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='264']">
    <mods:originInfo eventType="publication">
      <xsl:apply-templates select="marc:subfield" />
    </mods:originInfo>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='264']/marc:subfield[@code='a']">
    <mods:place>
      <mods:placeTerm type="text">
        <xsl:value-of select="text()" />
      </mods:placeTerm>
    </mods:place>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='264']/marc:subfield[@code='b']">
    <mods:publisher>
      <xsl:value-of select="text()" />
    </mods:publisher>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='264']/marc:subfield[@code='c']">
    <xsl:variable name="normalizedDate" select="translate(.,'()[]','')"/>
    <xsl:choose>
      <xsl:when test="translate($normalizedDate, '123456789', '000000000') = '0000-00-00'">
        <mods:dateIssued encoding="w3cdtf"><xsl:value-of select="$normalizedDate"/></mods:dateIssued>
      </xsl:when>
      <xsl:when test="translate($normalizedDate, '123456789', '000000000') = '0000-'">
        <mods:dateIssued encoding="w3cdtf" point="start"><xsl:value-of select="substring-before($normalizedDate, '-')"/></mods:dateIssued>
      </xsl:when>
      <xsl:when test="translate($normalizedDate, '123456789', '000000000') = '0000-0000'">
        <mods:dateIssued encoding="w3cdtf" point="start"><xsl:value-of select="substring-before($normalizedDate, '-')"/></mods:dateIssued>
        <mods:dateIssued encoding="w3cdtf" point="end"><xsl:value-of select="substring-after($normalizedDate, '-')"/></mods:dateIssued>
      </xsl:when>
    </xsl:choose>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='022']">
    <mods:identifier type="issn">
      <xsl:value-of select="marc:subfield[@code='a']" />
    </mods:identifier>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='041']">
    <mods:language>
      <mods:languageTerm type="code" authority="rfc5646">
        <xsl:variable name="given" select="translate(marc:subfield[@code='a'],'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')" />
        <xsl:value-of select="document('classification:metadata:-1:children:rfc5646')/mycoreclass/categories/category[@ID=$given or label[translate(@text,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')=$given]][1]/@ID" />
      </mods:languageTerm>
    </mods:language>
  </xsl:template>

  <!-- wias: map DDC from 082 to an existing SDNB category (full value, then e.g. 621.3, then main class),
       otherwise MCRCategoryMerger fails with a NullPointerException on unknown categories like 530.09 -->
  <xsl:template match="marc:datafield[@tag='082']">
    <xsl:variable name="sdnb" select="document('classification:metadata:-1:children:SDNB')/mycoreclass/categories" />
    <xsl:variable name="given" select="normalize-space(marc:subfield[@code='a'])" />
    <xsl:variable name="id">
      <xsl:choose>
        <xsl:when test="$sdnb//category[@ID=$given]"><xsl:value-of select="$given" /></xsl:when>
        <xsl:when test="$sdnb//category[@ID=substring($given,1,5)]"><xsl:value-of select="substring($given,1,5)" /></xsl:when>
        <xsl:when test="$sdnb//category[@ID=substring($given,1,3)]"><xsl:value-of select="substring($given,1,3)" /></xsl:when>
      </xsl:choose>
    </xsl:variable>
    <xsl:if test="string-length($id) &gt; 0">
      <mods:classification displayLabel="sdnb" authority="sdnb">
        <xsl:value-of select="$id" />
      </mods:classification>
    </xsl:if>
  </xsl:template>

  <xsl:template match="marc:datafield[@tag='856']">
    <mods:location>
      <xsl:for-each select="../marc:datafield[@tag='856'][marc:subfield[@code='u']]">
        <mods:url><xsl:value-of select="marc:subfield[@code='u']"/></mods:url>
      </xsl:for-each>
    </mods:location>
  </xsl:template>

  <xsl:template match="*" />

</xsl:stylesheet>
