<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:mods="http://www.loc.gov/mods/v3"
  exclude-result-prefixes="xsl mods">

  <xsl:include href="copynodes.xsl" />

  <xsl:template match="mods:genre">
    <xsl:variable name="genre" select="normalize-space(translate(text(),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'))" />
    <mods:genre type="intern">
      <xsl:choose>

        <xsl:when test="$genre='trade journal'">journal</xsl:when>
        <xsl:when test="$genre='journal'">journal</xsl:when>
        <xsl:when test="$genre='journal article'">article</xsl:when>
        <xsl:when test="$genre='article'">article</xsl:when>
        <xsl:when test="$genre='article in press'">article</xsl:when>
        <xsl:when test="$genre='business article'">article</xsl:when>
        <xsl:when test="$genre='book' and ancestor::mods:relatedItem/@type='host'">collection</xsl:when>
        <xsl:when test="$genre='book'">book</xsl:when>
        <xsl:when test="$genre='book series'">series</xsl:when>
        <xsl:when test="$genre='conference paper'">chapter</xsl:when>
        <xsl:when test="$genre='conference abstract'">speech</xsl:when>
        <xsl:when test="$genre='conference proceedings'">proceedings</xsl:when>
        <xsl:when test="$genre='editorial'">preface</xsl:when>
        <xsl:when test="$genre='dissertation'">dissertation</xsl:when>
        <xsl:when test="$genre='working paper'">book</xsl:when>
        <xsl:when test="$genre='review'">article</xsl:when>
        <xsl:when test="$genre='book chapter'">chapter</xsl:when>
        <xsl:when test="$genre='letter'">article</xsl:when>
        <xsl:when test="$genre='note'">article</xsl:when>
        <xsl:when test="$genre='short survey'">article</xsl:when>
        <xsl:when test="$genre='erratum'">article</xsl:when>

        <!-- PubMed Publication Types -->
        <xsl:when test="$genre='biography'">interview</xsl:when>
        <xsl:when test="$genre='case reports'">article</xsl:when>
        <xsl:when test="$genre='classical article'">article</xsl:when>
        <xsl:when test="$genre='clinical study'">article</xsl:when>
        <xsl:when test="$genre='clinical trial'">article</xsl:when>
        <xsl:when test="$genre='comparative study'">article</xsl:when>
        <xsl:when test="$genre='controlled clinical trial'">article</xsl:when>
        <xsl:when test="$genre='corrected and republished article'">article</xsl:when>
        <xsl:when test="$genre='evaluation studies'">article</xsl:when>
        <xsl:when test="$genre='festschrift'">festschrift</xsl:when>
        <xsl:when test="$genre='historical article'">article</xsl:when>
        <xsl:when test="$genre='interview'">interview</xsl:when>
        <xsl:when test="$genre='introductory journal article'">preface</xsl:when>
        <xsl:when test="$genre='lectures'">speech</xsl:when>
        <xsl:when test="$genre='multicenter study'">article</xsl:when>
        <xsl:when test="$genre='newspaper article'">article</xsl:when>
        <xsl:when test="$genre='observational study'">article</xsl:when>
        <xsl:when test="$genre='published erratum'">article</xsl:when>
        <xsl:when test="$genre='randomized controlled trial'">article</xsl:when>
        <xsl:when test="$genre='study characteristics'">article</xsl:when>
        <xsl:when test="$genre='technical report'">article</xsl:when>

        <!-- ignore these publication types from Scopus and PubMed  -->
        <xsl:when test="$genre='conference review'">ignore</xsl:when>
        <xsl:when test="$genre='addresses'">ignore</xsl:when>
        <xsl:when test="$genre='autobiography'">ignore</xsl:when>
        <xsl:when test="$genre='bibliography'">ignore</xsl:when>
        <xsl:when test="$genre='book illustrations'">ignore</xsl:when>
        <xsl:when test="$genre='clinical conference'">ignore</xsl:when>
        <xsl:when test="$genre='clinical trial, phase i'">ignore</xsl:when>
        <xsl:when test="$genre='clinical trial, phase ii'">ignore</xsl:when>
        <xsl:when test="$genre='clinical trial, phase iii'">ignore</xsl:when>
        <xsl:when test="$genre='clinical trial, phase iv'">ignore</xsl:when>
        <xsl:when test="$genre='collected works'">ignore</xsl:when>
        <xsl:when test="$genre='comment'">ignore</xsl:when>
        <xsl:when test="$genre='congresses'">ignore</xsl:when>
        <xsl:when test="$genre='consensus development conference'">ignore</xsl:when>
        <xsl:when test="$genre='consensus development conference, nih'">ignore</xsl:when>
        <xsl:when test="$genre='dataset'">ignore</xsl:when>
        <xsl:when test="$genre='dictionary'">ignore</xsl:when>
        <xsl:when test="$genre='directory'">ignore</xsl:when>
        <xsl:when test="$genre='duplicate publication'">ignore</xsl:when>
        <xsl:when test="$genre='electronic supplementary materials'">ignore</xsl:when>
        <xsl:when test="$genre='english abstract'">ignore</xsl:when>
        <xsl:when test="$genre='ephemera'">ignore</xsl:when>
        <xsl:when test="$genre='government publications'">ignore</xsl:when>
        <xsl:when test="$genre='guideline'">ignore</xsl:when>
        <xsl:when test="$genre='interactive tutorial'">ignore</xsl:when>
        <xsl:when test="$genre='legal cases'">ignore</xsl:when>
        <xsl:when test="$genre='legislation'">ignore</xsl:when>
        <xsl:when test="$genre='meta analysis'">ignore</xsl:when>
        <xsl:when test="$genre='news'">ignore</xsl:when>
        <xsl:when test="$genre='overall'">ignore</xsl:when>
        <xsl:when test="$genre='patient education handout'">ignore</xsl:when>
        <xsl:when test="$genre='periodical index'">ignore</xsl:when>
        <xsl:when test="$genre='personal narratives'">ignore</xsl:when>
        <xsl:when test="$genre='pictorial works'">ignore</xsl:when>
        <xsl:when test="$genre='popular works'">ignore</xsl:when>
        <xsl:when test="$genre='portraits'">ignore</xsl:when>
        <xsl:when test="$genre='practice guideline'">ignore</xsl:when>
        <xsl:when test="$genre='pragmatic clinical trial'">ignore</xsl:when>
        <xsl:when test="$genre='publication components'">ignore</xsl:when>
        <xsl:when test="$genre='publication formats'">ignore</xsl:when>
        <xsl:when test="$genre='publication type category'">ignore</xsl:when>
        <xsl:when test="starts-with($genre,'research support')">ignore</xsl:when>
        <xsl:when test="$genre='retracted publication'">ignore</xsl:when>
        <xsl:when test="$genre='retraction of publication'">ignore</xsl:when>
        <xsl:when test="$genre='scientific integrity review'">ignore</xsl:when>
        <xsl:when test="$genre='support of research'">ignore</xsl:when>
        <xsl:when test="$genre='twin study'">ignore</xsl:when>
        <xsl:when test="$genre='validation studies'">ignore</xsl:when>
        <xsl:when test="$genre='video audio media'">ignore</xsl:when>
        <xsl:when test="$genre='webcasts'">ignore</xsl:when>

<!-- TODO:
        Abstract Report
        Book Review
        Patent
        Press Release
        Report
        Multi-volume Reference Works
        Newsletter
        Newspaper
 -->
        <xsl:otherwise>
          <xsl:text>other</xsl:text>
          <xsl:message>Imported genre can not be mapped to internal genre: <xsl:value-of select="." /></xsl:message>
        </xsl:otherwise>
      </xsl:choose>
    </mods:genre>
  </xsl:template>

</xsl:stylesheet>