<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
    xmlns:fo="http://www.w3.org/1999/XSL/Format" 
    xmlns:barcode="http://barcode4j.krysalis.org/ns">

    <!-- Attribute for dynamic page height and width -->
    <xsl:variable name="sticker-height" select="/patientIdStickers/@sticker-height"/>
    <xsl:variable name="sticker-width" select="/patientIdStickers/@sticker-width"/>

    <!-- Calculate effective body height (page height minus margins) -->
    <xsl:variable name="body-height" select="concat(number(substring-before($sticker-height, 'mm')) - 4, 'mm')"/>
    <xsl:variable name="body-height-value" select="number(substring-before($sticker-height, 'mm')) - 4"/>
    
    <!-- Calculate responsive font sizes based on sticker dimensions -->
    <xsl:variable name="base-font-size" select="number(substring-before($sticker-width, 'mm')) div 15"/>
    
    <!-- Customizable font sizes with defaults -->
    <xsl:variable name="label-font-size">
        <xsl:choose>
            <xsl:when test="/patientIdStickers/@label-font-size">
                <xsl:value-of select="/patientIdStickers/@label-font-size"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$base-font-size * 0.5"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:variable>
    
    <xsl:variable name="title-font-size">
        <xsl:choose>
            <xsl:when test="/patientIdStickers/@value-font-size">
                <xsl:value-of select="/patientIdStickers/@value-font-size"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$base-font-size * 1.2"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:variable>
        
    <!-- Customizable font styles with defaults -->
    <xsl:variable name="label-font-family">
        <xsl:choose>
            <xsl:when test="/patientIdStickers/@label-font-family">
                <xsl:value-of select="/patientIdStickers/@label-font-family"/>
            </xsl:when>
            <xsl:otherwise>IBM Plex Sans Arabic</xsl:otherwise>
        </xsl:choose>
    </xsl:variable>
    
    <xsl:variable name="value-font-family">
        <xsl:choose>
            <xsl:when test="/patientIdStickers/@value-font-family">
                <xsl:value-of select="/patientIdStickers/@value-font-family"/>
            </xsl:when>
            <xsl:otherwise>IBM Plex Sans Arabic Bold</xsl:otherwise>
        </xsl:choose>
    </xsl:variable>

    <!-- Vertical gap between fields -->
    <xsl:variable name="field-vertical-gap">
        <xsl:choose>
            <xsl:when test="/patientIdStickers/@field-vertical-gap">
                <xsl:value-of select="/patientIdStickers/@field-vertical-gap"/>
            </xsl:when>
            <xsl:otherwise>1mm</xsl:otherwise>
        </xsl:choose>
    </xsl:variable>
    
    <!-- Field identification using message keys -->
    <xsl:variable name="patientIdKey" select="/*/patientIdSticker/@patientIdKey"/>
    <xsl:variable name="patientSecondaryIdKey" select="/*/patientIdSticker/@patientSecondaryIdKey"/>
    <xsl:variable name="patientNameKey" select="/*/patientIdSticker/@patientNameKey"/>
    <xsl:variable name="genderKey" select="/*/patientIdSticker/@genderKey"/>
    <xsl:variable name="dobKey" select="/*/patientIdSticker/@dobKey"/>
    <xsl:variable name="ageKey" select="/*/patientIdSticker/@ageKey"/>
    
    <xsl:template match="patientIdStickers">
        <fo:root>
            <fo:layout-master-set>
                <fo:simple-page-master master-name="sticker" 
                    page-width="{$sticker-width}" page-height="{$sticker-height}"
                    margin="3mm">
                    <fo:region-body margin="0"/>
                </fo:simple-page-master>
            </fo:layout-master-set>
            
            <xsl:apply-templates select="patientIdSticker"/>
        </fo:root>
    </xsl:template>

    <xsl:template match="patientIdSticker">
        <!-- Check if barcode exists -->
        <xsl:variable name="has-barcode" select="boolean(barcode/@barcodeValue != '')"/>
        
        <!-- Get barcode type from attribute or default to Code128 -->
        <xsl:variable name="barcode-type">
            <xsl:choose>
                <xsl:when test="@barcode-type">
                    <xsl:value-of select="@barcode-type"/>
                </xsl:when>
                <xsl:otherwise>Code128</xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <!-- Get custom barcode height or use calculated default -->
        <xsl:variable name="barcode-height-value">
            <xsl:choose>
                <xsl:when test="@barcode-height">
                    <xsl:value-of select="@barcode-height"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="$body-height-value * 0.28"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <!-- Adjust section heights based on barcode presence -->
        <xsl:variable name="header-height">
            <xsl:choose>
                <xsl:when test="header"><xsl:value-of select="concat($body-height-value * 0.12, 'mm')"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="concat(0, 'mm')"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <xsl:variable name="barcode-height">
            <xsl:choose>
                <xsl:when test="$has-barcode"><xsl:value-of select="concat($barcode-height-value, 'mm')"/></xsl:when>
                <xsl:otherwise><xsl:value-of select="concat(0, 'mm')"/></xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <xsl:variable name="table-height">
            <xsl:choose>
                <xsl:when test="$has-barcode and header">
                    <xsl:value-of select="concat($body-height-value * 0.60, 'mm')"/>
                </xsl:when>
                <xsl:when test="$has-barcode and not(header)">
                    <xsl:value-of select="concat($body-height-value * 0.72, 'mm')"/>
                </xsl:when>
                <xsl:when test="not($has-barcode) and header">
                    <xsl:value-of select="concat($body-height-value * 0.78, 'mm')"/>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="concat($body-height-value, 'mm')"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>
        
        <fo:page-sequence master-reference="sticker">
            <fo:flow flow-name="xsl-region-body">
                <!-- Main container with dynamic height -->
                <fo:block-container height="{$body-height}" display-align="before">
                    <!-- Header section -->
                    <xsl:if test="header">
                        <fo:block-container height="{$header-height}" display-align="center">
                            <fo:block>
                                <fo:table width="100%">
                                    <fo:table-column column-width="50%"/>
                                    <fo:table-column column-width="50%"/>
                                    <fo:table-body>
                                        <fo:table-row>
                                            <fo:table-cell>
                                                <xsl:if test="header/branding/logo != ''">
                                                    <fo:block>
                                                        <fo:external-graphic 
                                                            max-height="{number(substring-before($header-height, 'mm')) - 2}mm"
                                                            max-width="80%"
                                                            content-width="scale-down-to-fit"
                                                            content-height="scale-down-to-fit"
                                                            scaling="uniform"
                                                            src="{header/branding/logo}"/>
                                                    </fo:block>
                                                </xsl:if>
                                            </fo:table-cell>
                                            <fo:table-cell>
                                                <fo:block text-align="right" font-size="{$label-font-size}pt" line-height="{$label-font-size + 1}pt">
                                                    <xsl:value-of select="header/headerText"/>
                                                </fo:block>
                                            </fo:table-cell>
                                        </fo:table-row>
                                    </fo:table-body>
                                </fo:table>
                            </fo:block>
                        </fo:block-container>
                    </xsl:if>

                    <!-- Barcode section (if present) -->
                    <xsl:if test="$has-barcode">
                        <fo:block-container display-align="center" height="{$barcode-height}">
                            <fo:block text-align="center" width="100%">
                                <fo:instream-foreign-object width="100%" scaling="uniform" content-height="{number(substring-before($barcode-height, 'mm')) - 1}mm" content-width="{$sticker-width}">
                                    <barcode:barcode message="{barcode/@barcodeValue}">
                                        <xsl:element name="barcode:{$barcode-type}">
                                            <barcode:human-readable>None</barcode:human-readable>
                                            <barcode:height>
                                                <xsl:value-of select="number(substring-before($barcode-height, 'mm')) - 1"/>
                                                <xsl:text>mm</xsl:text>
                                            </barcode:height>
                                        </xsl:element>
                                    </barcode:barcode>
                                </fo:instream-foreign-object>
                            </fo:block>
                        </fo:block-container>
                        
                        <!-- Separator line with no space above -->
                        <fo:block padding-top="0"> 
                            <fo:leader leader-pattern="rule" leader-length="100%" rule-thickness="0.5pt" rule-style="solid" color="#008080"/>
                        </fo:block>
                    </xsl:if>
                    
                    <!-- Main data section -->
                    <fo:block-container height="{$table-height}" display-align="before">
                        <xsl:if test="fields/field[@label = $patientSecondaryIdKey]">
                            <fo:block-container margin-bottom="{$field-vertical-gap}">
                                <fo:block font-size="{$label-font-size}pt" font-weight="normal" font-family="{$label-font-family}" color="#444444">
                                    <xsl:value-of select="fields/field[@label = $patientSecondaryIdKey]/@label"/>
                                </fo:block>
                                <fo:block font-size="{$title-font-size}pt" font-weight="bold" font-family="{$value-font-family}"  margin-top="0.2mm">
                                    <xsl:value-of select="fields/field[@label = $patientSecondaryIdKey]"/>
                                </fo:block>
                            </fo:block-container>
                        </xsl:if>

                        <xsl:if test="fields/field[@label = $patientIdKey]">
                            <fo:block-container margin-bottom="{$field-vertical-gap}">
                                <fo:block font-size="{$label-font-size}pt" font-weight="normal" font-family="{$label-font-family}" color="#444444">
                                    <xsl:value-of select="fields/field[@label = $patientIdKey]/@label"/>
                                </fo:block>
                                <fo:block font-size="{$title-font-size}pt" font-weight="bold" font-family="{$value-font-family}" margin-top="0.2mm">
                                    <xsl:value-of select="fields/field[@label = $patientIdKey]"/>
                                </fo:block>
                            </fo:block-container>
                        </xsl:if>

                        <xsl:if test="fields/field[@label = $patientNameKey]">
                            <fo:block-container margin-bottom="{$field-vertical-gap}">
                                <fo:block font-size="{$label-font-size}pt" font-weight="normal" font-family="{$label-font-family}" color="#444444">
                                    <xsl:value-of select="fields/field[@label = $patientNameKey]/@label"/>
                                </fo:block>
                                <fo:block font-size="{$title-font-size}pt" font-weight="bold" font-family="{$value-font-family}" margin-top="0.2mm">
                                    <xsl:value-of select="fields/field[@label = $patientNameKey]"/>
                                </fo:block>
                            </fo:block-container>
                        </xsl:if>
                        
                        <xsl:for-each select="fields/field[
                            @label != $patientNameKey and
                            @label != $patientSecondaryIdKey and
                            @label != $patientIdKey]">
                            <fo:block-container margin-bottom="{$field-vertical-gap}">
                                <fo:block font-size="{$label-font-size}pt" font-weight="normal" font-family="{$label-font-family}" color="#444444">
                                    <xsl:value-of select="@label"/>
                                </fo:block>
                                <fo:block font-size="{$title-font-size}pt" font-weight="bold" font-family="{$value-font-family}" margin-top="0.2mm">
                                    <xsl:value-of select="."/>
                                </fo:block>
                            </fo:block-container>
                        </xsl:for-each>
                    </fo:block-container>
                </fo:block-container>
            </fo:flow>
        </fo:page-sequence>
    </xsl:template>
</xsl:stylesheet>
