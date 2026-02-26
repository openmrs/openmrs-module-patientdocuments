<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:template match="/">
        <fo:root>
            <fo:layout-master-set>
                <fo:simple-page-master master-name="A4" page-height="29.7cm" page-width="21cm" margin="1.5cm">
                    <fo:region-body margin-top="2.5cm" margin-bottom="1.5cm"/>
                    <fo:region-before extent="2.5cm"/>
                    <fo:region-after extent="1.5cm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <xsl:apply-templates select="encounters/encounter"/>
        </fo:root>
    </xsl:template>

    <xsl:template name="render-header-field">
        <xsl:param name="label"/>
        <xsl:param name="value"/>
        <xsl:if test="$value">
            <fo:block>
                <xsl:choose>
                    <xsl:when test="$label = 'patientName'">
                        <fo:inline font-weight="bold" font-size="12pt"><xsl:value-of select="$label"/>: </fo:inline><xsl:value-of select="$value"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="$label"/>: <xsl:value-of select="$value"/>
                    </xsl:otherwise>
                </xsl:choose>
            </fo:block>
        </xsl:if>
    </xsl:template>

    <xsl:template match="encounter">
        <fo:page-sequence master-reference="A4">
            <fo:static-content flow-name="xsl-region-before">
                <xsl:variable name="hasHeader" select="patientName or location or visitDate or encounterDate or visitStartDate or visitEndDate or visitType or formNameInHeader or patientIdentifiers or personAttributes or visitAttributes"/>
                <fo:block border-bottom="1pt solid #ccc" padding-bottom="2mm" font-size="10pt" color="#555">
                    <xsl:if test="$hasHeader">
                        <fo:table width="100%" table-layout="fixed">
                            <fo:table-column column-width="50%"/>
                            <fo:table-column column-width="50%"/>
                            <fo:table-body>
                                <fo:table-row>
                                    <fo:table-cell>
                                        <xsl:choose>
                                            <xsl:when test="patientName or location or visitStartDate or personAttributes or formNameInHeader">
                                                <xsl:if test="patientName">
                                                    <fo:block font-weight="bold" font-size="12pt">Patient Name: <xsl:value-of select="patientName"/></fo:block>
                                                </xsl:if>
                                                <xsl:if test="location">
                                                    <fo:block>Location: <xsl:value-of select="location"/></fo:block>
                                                </xsl:if>
                                                <xsl:if test="visitStartDate">
                                                    <fo:block>Visit Start: <xsl:value-of select="visitStartDate"/></fo:block>
                                                </xsl:if>
                                                <xsl:if test="personAttributes">
                                                    <xsl:for-each select="personAttributes/attribute">
                                                        <fo:block><xsl:value-of select="@type"/>: <xsl:value-of select="."/></fo:block>
                                                    </xsl:for-each>
                                                </xsl:if>
                                                <xsl:if test="formNameInHeader">
                                                    <fo:block>Form: <xsl:value-of select="formName"/></fo:block>
                                                </xsl:if>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:block/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:table-cell>
                                    <fo:table-cell text-align="right">
                                        <xsl:choose>
                                            <xsl:when test="visitDate or encounterDate or visitEndDate or visitType or patientIdentifiers or visitAttributes">
                                                <xsl:if test="visitDate">
                                                    <fo:block>Visit Date: <xsl:value-of select="visitDate"/></fo:block>
                                                </xsl:if>
                                                <xsl:if test="encounterDate">
                                                    <fo:block>Encounter Date: <xsl:value-of select="encounterDate"/></fo:block>
                                                </xsl:if>
                                                <xsl:if test="visitEndDate">
                                                    <fo:block>Visit End: <xsl:value-of select="visitEndDate"/></fo:block>
                                                </xsl:if>
                                                <xsl:if test="visitType">
                                                    <fo:block>Visit Type: <xsl:value-of select="visitType"/></fo:block>
                                                </xsl:if>
                                                <xsl:if test="patientIdentifiers">
                                                    <xsl:for-each select="patientIdentifiers/identifier">
                                                        <fo:block><xsl:value-of select="@type"/>: <xsl:value-of select="."/></fo:block>
                                                    </xsl:for-each>
                                                </xsl:if>
                                                <xsl:if test="visitAttributes">
                                                    <xsl:for-each select="visitAttributes/attribute">
                                                        <fo:block><xsl:value-of select="@type"/>: <xsl:value-of select="."/></fo:block>
                                                    </xsl:for-each>
                                                </xsl:if>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <fo:block/>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </fo:table-cell>
                                </fo:table-row>
                            </fo:table-body>
                        </fo:table>
                    </xsl:if>
                </fo:block>
            </fo:static-content>

            <fo:static-content flow-name="xsl-region-after">
                <fo:block text-align="center" border-top="0.5pt solid #eee" padding-top="2mm">
                    <fo:block font-size="8pt" color="#777" margin-bottom="1mm">
                        <xsl:value-of select="printedBy"/>
                    </fo:block>
                    <xsl:if test="customFooterText">
                        <fo:block font-size="8pt" color="#777" margin-bottom="1mm">
                            <xsl:value-of select="customFooterText"/>
                        </fo:block>
                    </xsl:if>
                    <fo:block font-size="8pt" color="#999">
                        Page <fo:page-number/>
                    </fo:block>
                </fo:block>
            </fo:static-content>

            <fo:flow flow-name="xsl-region-body">
                <fo:block font-size="16pt" font-weight="bold" text-align="center" margin-bottom="8mm" color="#2c3e50">
                    <xsl:value-of select="formName"/>
                </fo:block>

                <xsl:apply-templates select="pages/page"/>
            </fo:flow>
        </fo:page-sequence>
    </xsl:template>

    <xsl:template match="page">
        <fo:block keep-together.within-page="always" margin-bottom="5mm">
            <xsl:if test="@label">
                <fo:block font-size="13pt" font-weight="bold" color="#005f87" margin-top="4mm" margin-bottom="2mm" border-bottom="0.5pt solid #005f87">
                    <xsl:value-of select="@label"/>
                </fo:block>
            </xsl:if>

            <xsl:apply-templates select="section"/> </fo:block>
    </xsl:template>

    <xsl:template match="section">
        <fo:block margin-left="2mm" margin-bottom="4mm">
            <xsl:if test="@label != ''">
                <fo:block font-size="11pt" font-weight="bold" background-color="#eef" padding="1mm" margin-bottom="2mm">
                    <xsl:value-of select="@label"/>
                </fo:block>
            </xsl:if>

            <xsl:choose>
                <xsl:when test="question or markdown">
                    <fo:table width="100%" table-layout="fixed">
                        <fo:table-column column-width="60%"/>
                        <fo:table-column column-width="40%"/>
                        <fo:table-body>
                            <xsl:apply-templates select="question | markdown"/>
                        </fo:table-body>
                    </fo:table>
                </xsl:when>
                <xsl:otherwise>
                    <fo:block font-size="9pt" font-style="italic" color="#888" margin-left="2mm" padding="1mm">
                        No data recorded for this section.
                    </fo:block>
                </xsl:otherwise>
            </xsl:choose>
        </fo:block>
    </xsl:template>

    <xsl:template match="question">
        <fo:table-row keep-together.within-page="always">
            <fo:table-cell padding="1.5mm" border-bottom="0.5pt solid #eee">
                <fo:block font-size="10pt" color="#333">
                    <xsl:value-of select="@label"/>
                </fo:block>
            </fo:table-cell>

            <fo:table-cell padding="1.5mm" border-bottom="0.5pt solid #eee" background-color="#fafafa">
                <fo:block font-size="10pt" font-weight="bold" color="black">
                    <xsl:value-of select="."/>
                </fo:block>
            </fo:table-cell>
        </fo:table-row>
    </xsl:template>

    <xsl:template match="markdown">
        <fo:table-row>
            <fo:table-cell number-columns-spanned="2" padding="2mm">
                <fo:block font-size="9pt" font-style="italic" color="#666" background-color="#ffffee" border="0.5pt dashed #ccc" padding="2mm">
                    <xsl:value-of select="."/>
                </fo:block>
            </fo:table-cell>
        </fo:table-row>
    </xsl:template>

</xsl:stylesheet>