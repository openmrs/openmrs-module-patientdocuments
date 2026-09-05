<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format">

    <xsl:output method="xml" indent="yes"/>

    <!-- Page frame from renderer attributes: XSLT 1.0 has no unit arithmetic, so
         VisitSummaryPageLayout measures and this stylesheet only interpolates. -->
    <xsl:variable name="page-height" select="/visitSummary/@page-height"/>
    <xsl:variable name="page-width"  select="/visitSummary/@page-width"/>
    <xsl:variable name="side-margin" select="/visitSummary/@side-margin"/>
    <xsl:variable name="logo-column-width"  select="/visitSummary/@logo-column-width"/>
    <xsl:variable name="logo-graphic-width" select="/visitSummary/@logo-graphic-width"/>

    <!-- standard | narrow | compact, banded on the content width by the renderer. An
         absent attribute reads as the standard profile, so a caller that predates the
         responsive work still gets the layout it always got. -->
    <xsl:variable name="layout-profile" select="/visitSummary/@layout-profile"/>

    <!-- The label/value grids — patient information and vitals — reflow to one column from
         narrow paper down. They are not data tables: each cell holds one unpredictable value
         such as an identifier or a location, and a cell too small for it does not wrap, it
         lets FOP draw it across its neighbour. -->
    <xsl:variable name="stack-fields"
        select="$layout-profile = 'narrow' or $layout-profile = 'compact'"/>

    <!-- Font families matching bundled IBM Plex Sans Arabic fonts -->
    <xsl:variable name="label-font-family">IBM Plex Sans Arabic</xsl:variable>
    <xsl:variable name="value-font-family">IBM Plex Sans Arabic Bold</xsl:variable>

    <!-- Section heading colour — ink-efficient dark grey for low-resource sites -->
    <xsl:variable name="section-heading-color">#333333</xsl:variable>

    <!-- Root template -->
    <xsl:template match="/visitSummary">
        <fo:root>
            <fo:layout-master-set>
                <fo:simple-page-master master-name="visit-summary-page"
                    page-height="{$page-height}" page-width="{$page-width}"
                    margin-top="15mm" margin-bottom="15mm"
                    margin-left="{$side-margin}" margin-right="{$side-margin}">
                    <!-- The footer's extent and the body's clearance stay fixed while the side
                         margins scale: the footer is sized to its own two rows of 7pt text,
                         not to the paper. That holds at standard width; below it the rows
                         wrap and overflow this region. -->
                    <fo:region-body margin-bottom="10mm"/>
                    <fo:region-after extent="10mm"/>
                </fo:simple-page-master>
            </fo:layout-master-set>

            <fo:page-sequence master-reference="visit-summary-page">
                <fo:static-content flow-name="xsl-region-after">
                    <xsl:call-template name="footer"/>
                </fo:static-content>

                <fo:flow flow-name="xsl-region-body">
                    <!-- Sections render in document order: the renderer emits them sorted
                         by each section's configured getOrder(), with section-error and
                         section-notice elements sitting next to the section they belong to.
                         The footer element is page furniture (rendered by the static-content
                         region above) and is skipped here by its empty match template. -->
                    <xsl:apply-templates select="*"/>

                    <!-- Anchor for "Page N of M": the footer's fo:page-number-citation
                         resolves against this id on the document's last block. -->
                    <fo:block id="visit-summary-end"/>
                </fo:flow>
            </fo:page-sequence>
        </fo:root>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Facility header band. The logo cell is emitted even when the logo is
         absent, so the rest of the band does not shift or collapse. That column and its
         graphic scale with the content box (28mm and 24mm on A4) so they do not eat a
         narrow page; the other two columns stay proportional.
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="facilityHeader">
        <fo:block font-family="{$label-font-family}" margin-bottom="3mm"
            border-bottom="0.5pt solid #cccccc" padding-bottom="2mm">
            <fo:table width="100%" table-layout="fixed">
                <fo:table-column column-width="{$logo-column-width}"/>
                <fo:table-column column-width="proportional-column-width(58)"/>
                <fo:table-column column-width="proportional-column-width(42)"/>
                <fo:table-body>
                    <fo:table-row>
                        <fo:table-cell display-align="center">
                            <fo:block>
                                <xsl:if test="logoData != ''">
                                    <fo:external-graphic src="{logoData}"
                                        width="{$logo-graphic-width}" content-width="scale-down-to-fit"
                                        content-height="12mm" scaling="uniform"/>
                                </xsl:if>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell display-align="center" padding="0 2mm">
                            <xsl:if test="facilityName != ''">
                                <fo:block font-size="13pt" font-weight="bold"
                                    font-family="{$value-font-family}">
                                    <xsl:value-of select="facilityName"/>
                                </fo:block>
                            </xsl:if>
                            <xsl:if test="facilityAddress != ''">
                                <fo:block font-size="8pt" margin-top="0.5mm">
                                    <xsl:value-of select="facilityAddress"/>
                                </fo:block>
                            </xsl:if>
                            <xsl:if test="facilityPhone != ''">
                                <fo:block font-size="8pt" margin-top="0.5mm">
                                    <xsl:value-of select="facilityPhone"/>
                                </fo:block>
                            </xsl:if>
                            <!-- FOP errors on a table cell containing no block, so this empty block
                                 must stay when the facility has no details -->
                            <fo:block/>
                        </fo:table-cell>
                        <fo:table-cell display-align="center">
                            <xsl:if test="documentTitle != ''">
                                <fo:block font-size="11pt" font-weight="bold"
                                    font-family="{$value-font-family}" text-align="right"
                                    color="{$section-heading-color}">
                                    <xsl:value-of select="documentTitle"/>
                                </fo:block>
                            </xsl:if>
                            <xsl:if test="visitDate != ''">
                                <fo:block font-size="9pt" text-align="right" margin-top="0.5mm">
                                    <xsl:value-of select="visitDate"/>
                                </fo:block>
                            </xsl:if>
                            <!-- FOP errors on a table cell containing no block, so this empty block
                                 must stay when title and date are absent -->
                            <fo:block/>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-body>
            </fo:table>
        </fo:block>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Patient information — seven named fields. A proportional grid at standard width,
         one "Label: value" line per field from narrow down. Labels come from @lbl-*
         attributes set by the renderer; the age suffix is omitted when the age is unknown.
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="patientInfo">
        <xsl:variable name="dob-with-age">
            <xsl:value-of select="dateOfBirth"/>
            <xsl:if test="age != ''">
                <xsl:text> (</xsl:text>
                <xsl:value-of select="@lbl-age"/>
                <xsl:text>: </xsl:text>
                <xsl:value-of select="age"/>
                <xsl:text>)</xsl:text>
            </xsl:if>
        </xsl:variable>
        <fo:block font-family="{$label-font-family}" margin-bottom="2.5mm">
            <fo:block font-size="11pt" font-weight="bold" font-family="{$value-font-family}"
                margin-bottom="1mm" color="{$section-heading-color}"
                border-bottom="0.5pt solid #cccccc" padding-bottom="0.5mm">
                <xsl:value-of select="@heading"/>
            </fo:block>
            <xsl:choose>
                <!-- The grid's trailing spacer cell has no stacked counterpart: it padded a
                     row out to four columns, and there are no columns to pad here. -->
                <xsl:when test="$stack-fields">
                    <xsl:call-template name="field-line">
                        <xsl:with-param name="label" select="@lbl-patient-name"/>
                        <xsl:with-param name="value" select="patientName"/>
                    </xsl:call-template>
                    <xsl:call-template name="field-line">
                        <xsl:with-param name="label" select="@lbl-patient-id"/>
                        <xsl:with-param name="value" select="patientId"/>
                    </xsl:call-template>
                    <xsl:call-template name="field-line">
                        <xsl:with-param name="label" select="@lbl-dob"/>
                        <xsl:with-param name="value" select="$dob-with-age"/>
                    </xsl:call-template>
                    <xsl:call-template name="field-line">
                        <xsl:with-param name="label" select="@lbl-gender"/>
                        <xsl:with-param name="value" select="gender"/>
                    </xsl:call-template>
                    <xsl:call-template name="field-line">
                        <xsl:with-param name="label" select="@lbl-visit-date"/>
                        <xsl:with-param name="value" select="visitDate"/>
                    </xsl:call-template>
                    <xsl:call-template name="field-line">
                        <xsl:with-param name="label" select="@lbl-visit-type"/>
                        <xsl:with-param name="value" select="visitType"/>
                    </xsl:call-template>
                    <xsl:call-template name="field-line">
                        <xsl:with-param name="label" select="@lbl-location"/>
                        <xsl:with-param name="value" select="visitLocation"/>
                    </xsl:call-template>
                </xsl:when>
                <xsl:otherwise>
                    <fo:table width="100%" table-layout="fixed">
                        <fo:table-column column-width="proportional-column-width(1)"/>
                        <fo:table-column column-width="proportional-column-width(1)"/>
                        <fo:table-column column-width="proportional-column-width(1)"/>
                        <fo:table-column column-width="proportional-column-width(1)"/>
                        <fo:table-body>
                            <fo:table-row>
                                <xsl:call-template name="patient-field">
                                    <xsl:with-param name="label" select="@lbl-patient-name"/>
                                    <xsl:with-param name="value" select="patientName"/>
                                </xsl:call-template>
                                <xsl:call-template name="patient-field">
                                    <xsl:with-param name="label" select="@lbl-patient-id"/>
                                    <xsl:with-param name="value" select="patientId"/>
                                </xsl:call-template>
                                <xsl:call-template name="patient-field">
                                    <xsl:with-param name="label" select="@lbl-dob"/>
                                    <xsl:with-param name="value" select="$dob-with-age"/>
                                </xsl:call-template>
                                <xsl:call-template name="patient-field">
                                    <xsl:with-param name="label" select="@lbl-gender"/>
                                    <xsl:with-param name="value" select="gender"/>
                                </xsl:call-template>
                            </fo:table-row>
                            <fo:table-row>
                                <xsl:call-template name="patient-field">
                                    <xsl:with-param name="label" select="@lbl-visit-date"/>
                                    <xsl:with-param name="value" select="visitDate"/>
                                </xsl:call-template>
                                <xsl:call-template name="patient-field">
                                    <xsl:with-param name="label" select="@lbl-visit-type"/>
                                    <xsl:with-param name="value" select="visitType"/>
                                </xsl:call-template>
                                <xsl:call-template name="patient-field">
                                    <xsl:with-param name="label" select="@lbl-location"/>
                                    <xsl:with-param name="value" select="visitLocation"/>
                                </xsl:call-template>
                                <fo:table-cell padding="0.5mm 1mm"><fo:block/></fo:table-cell>
                            </fo:table-row>
                        </fo:table-body>
                    </fo:table>
                </xsl:otherwise>
            </xsl:choose>
        </fo:block>
    </xsl:template>

    <!-- One stacked "Label: value" line, shared by the two label/value grids. The value keeps
         the bold face it has in the grid, so the pair still reads at a glance on one line. -->
    <xsl:template name="field-line">
        <xsl:param name="label"/>
        <xsl:param name="value"/>
        <fo:block font-size="9pt" padding="0.5mm 0">
            <fo:inline color="#444444">
                <xsl:value-of select="$label"/>
                <xsl:text>: </xsl:text>
            </fo:inline>
            <fo:inline font-weight="bold" font-family="{$value-font-family}">
                <xsl:value-of select="$value"/>
            </fo:inline>
        </fo:block>
    </xsl:template>

    <!-- One labelled field cell of the patient block. -->
    <xsl:template name="patient-field">
        <xsl:param name="label"/>
        <xsl:param name="value"/>
        <fo:table-cell padding="0.5mm 1mm">
            <fo:block font-size="8pt" color="#444444">
                <xsl:value-of select="$label"/>
            </fo:block>
            <fo:block font-size="10pt" font-weight="bold" font-family="{$value-font-family}">
                <xsl:value-of select="$value"/>
            </fo:block>
        </fo:table-cell>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Vitals — a 4-column grid of @label/@value pairs at standard width, sharing the
         patient-information grid's columns so the two blocks line up. One "Label: value"
         line per vital from narrow down.
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="vitals">
        <fo:block font-family="{$label-font-family}" margin-bottom="2.5mm">
            <fo:block font-size="11pt" font-weight="bold" font-family="{$value-font-family}"
                margin-bottom="1mm" color="{$section-heading-color}"
                border-bottom="0.5pt solid #cccccc" padding-bottom="0.5mm">
                <xsl:value-of select="@heading"/>
            </fo:block>
            <xsl:choose>
                <xsl:when test="vital and $stack-fields">
                    <xsl:for-each select="vital">
                        <xsl:call-template name="field-line">
                            <xsl:with-param name="label" select="@label"/>
                            <xsl:with-param name="value" select="@value"/>
                        </xsl:call-template>
                    </xsl:for-each>
                </xsl:when>
                <xsl:when test="vital">
                    <fo:table width="100%" table-layout="fixed">
                        <fo:table-column column-width="proportional-column-width(1)"/>
                        <fo:table-column column-width="proportional-column-width(1)"/>
                        <fo:table-column column-width="proportional-column-width(1)"/>
                        <fo:table-column column-width="proportional-column-width(1)"/>
                        <fo:table-body>
                            <!-- Every 4th vital opens a row; the other three cells come from
                                 following-sibling, padded when the last row runs short. -->
                            <xsl:for-each select="vital[((position()-1) mod 4) = 0]">
                                <fo:table-row>
                                    <xsl:call-template name="vital-cell">
                                        <xsl:with-param name="label" select="@label"/>
                                        <xsl:with-param name="value" select="@value"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="vital-cell-or-spacer">
                                        <xsl:with-param name="vital" select="following-sibling::vital[1]"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="vital-cell-or-spacer">
                                        <xsl:with-param name="vital" select="following-sibling::vital[2]"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="vital-cell-or-spacer">
                                        <xsl:with-param name="vital" select="following-sibling::vital[3]"/>
                                    </xsl:call-template>
                                </fo:table-row>
                            </xsl:for-each>
                        </fo:table-body>
                    </fo:table>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="no-data-block"/>
                </xsl:otherwise>
            </xsl:choose>
        </fo:block>
    </xsl:template>

    <!-- One vital: small grey label over the bold value. -->
    <xsl:template name="vital-cell">
        <xsl:param name="label"/>
        <xsl:param name="value"/>
        <fo:table-cell padding="1mm 2mm">
            <fo:block font-size="8pt" color="#444444">
                <xsl:value-of select="$label"/>
            </fo:block>
            <fo:block font-size="10pt" font-weight="bold" font-family="{$value-font-family}">
                <xsl:value-of select="$value"/>
            </fo:block>
        </fo:table-cell>
    </xsl:template>

    <!-- A trailing cell: the vital if the row reaches it, else a spacer. FOP errors on a cell
         with no block, so the spacer still carries one. -->
    <xsl:template name="vital-cell-or-spacer">
        <xsl:param name="vital"/>
        <xsl:choose>
            <xsl:when test="$vital">
                <xsl:call-template name="vital-cell">
                    <xsl:with-param name="label" select="$vital/@label"/>
                    <xsl:with-param name="value" select="$vital/@value"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <fo:table-cell><fo:block/></fo:table-cell>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Data sections — the shared arrangement layer behind every multi-column table.

         A section states its columns once, as a spec: the label the renderer resolved into
         @col-*, the row attributes the value is read from, and the standard-width column
         width. That one spec feeds both arrangements — the table, and the stacked
         "Label: value" block it becomes once the columns stop fitting — so neither owns a
         label and the two cannot drift apart.

         The stacked arrangement stays inside a single-column table rather than becoming
         loose blocks, so row backgrounds, borders and spanning group headings keep working.
         ═══════════════════════════════════════════════════ -->
    <xsl:template name="data-section">
        <xsl:param name="heading"/>
        <xsl:param name="rows"/>
        <!-- Rows gathered under a spanning heading. Only lab results group, so this
             defaults to an empty node-set. -->
        <xsl:param name="groups" select="/.."/>
        <xsl:param name="standard-columns"/>
        <!-- The profile at which this section's columns stop fitting: two columns still
             sit side by side on narrow paper, three or more do not. -->
        <xsl:param name="stack-from" select="'narrow'"/>
        <!-- Column 1 usually names the row — the diagnosis, the drug — so it leads the
             stacked block as a heading rather than as another labelled line. Lab results
             turn this off: a result is only safe to read as "Test: …". -->
        <xsl:param name="lead-heading" select="true()"/>
        <xsl:param name="row-indent"/>
        <xsl:param name="group-row-indent"/>
        <xsl:param name="label-1"/>
        <xsl:param name="attr-1"/>
        <xsl:param name="label-2"/>
        <xsl:param name="attr-2"/>
        <xsl:param name="label-3"/>
        <xsl:param name="attr-3"/>
        <xsl:param name="label-4"/>
        <xsl:param name="attr-4"/>

        <xsl:variable name="stacked"
            select="$layout-profile = 'compact'
                    or ($layout-profile = 'narrow' and $stack-from = 'narrow')"/>
        <!-- A group heading spans whatever the arrangement actually laid down, so the
             stacked single column does not inherit the table's column count. -->
        <xsl:variable name="group-heading-span">
            <xsl:choose>
                <xsl:when test="$stacked">1</xsl:when>
                <xsl:otherwise>
                    <xsl:value-of select="number($attr-1 != '') + number($attr-2 != '')
                                          + number($attr-3 != '') + number($attr-4 != '')"/>
                </xsl:otherwise>
            </xsl:choose>
        </xsl:variable>

        <fo:block font-family="{$label-font-family}" margin-bottom="2.5mm">
            <fo:block font-size="11pt" font-weight="bold" font-family="{$value-font-family}"
                margin-bottom="1mm" color="{$section-heading-color}"
                border-bottom="0.5pt solid #cccccc" padding-bottom="0.5mm">
                <xsl:value-of select="$heading"/>
            </fo:block>
            <xsl:choose>
                <xsl:when test="$rows or $groups">
                    <fo:table width="100%" table-layout="fixed">
                        <xsl:choose>
                            <xsl:when test="$stacked">
                                <fo:table-column column-width="100%"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:copy-of select="$standard-columns"/>
                            </xsl:otherwise>
                        </xsl:choose>
                        <xsl:if test="not($stacked)">
                            <fo:table-header>
                                <fo:table-row background-color="#f5f5f5">
                                    <xsl:call-template name="data-header-cell">
                                        <xsl:with-param name="label" select="$label-1"/>
                                        <xsl:with-param name="attrs" select="$attr-1"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="data-header-cell">
                                        <xsl:with-param name="label" select="$label-2"/>
                                        <xsl:with-param name="attrs" select="$attr-2"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="data-header-cell">
                                        <xsl:with-param name="label" select="$label-3"/>
                                        <xsl:with-param name="attrs" select="$attr-3"/>
                                    </xsl:call-template>
                                    <xsl:call-template name="data-header-cell">
                                        <xsl:with-param name="label" select="$label-4"/>
                                        <xsl:with-param name="attrs" select="$attr-4"/>
                                    </xsl:call-template>
                                </fo:table-row>
                            </fo:table-header>
                        </xsl:if>
                        <fo:table-body>
                            <xsl:for-each select="$groups">
                                <fo:table-row>
                                    <fo:table-cell number-columns-spanned="{$group-heading-span}"
                                        padding="1mm 2mm" background-color="#fafafa"
                                        border-bottom="0.25pt solid #eeeeee">
                                        <fo:block font-size="9pt" font-weight="bold" color="#444444">
                                            <xsl:value-of select="@heading"/>
                                        </fo:block>
                                    </fo:table-cell>
                                </fo:table-row>
                                <xsl:for-each select="*">
                                    <xsl:call-template name="data-row">
                                        <xsl:with-param name="stacked" select="$stacked"/>
                                        <xsl:with-param name="indent" select="$group-row-indent"/>
                                        <xsl:with-param name="lead-heading" select="$lead-heading"/>
                                        <xsl:with-param name="label-1" select="$label-1"/>
                                        <xsl:with-param name="attr-1" select="$attr-1"/>
                                        <xsl:with-param name="label-2" select="$label-2"/>
                                        <xsl:with-param name="attr-2" select="$attr-2"/>
                                        <xsl:with-param name="label-3" select="$label-3"/>
                                        <xsl:with-param name="attr-3" select="$attr-3"/>
                                        <xsl:with-param name="label-4" select="$label-4"/>
                                        <xsl:with-param name="attr-4" select="$attr-4"/>
                                    </xsl:call-template>
                                </xsl:for-each>
                            </xsl:for-each>
                            <xsl:for-each select="$rows">
                                <xsl:call-template name="data-row">
                                    <xsl:with-param name="stacked" select="$stacked"/>
                                    <xsl:with-param name="indent" select="$row-indent"/>
                                    <xsl:with-param name="lead-heading" select="$lead-heading"/>
                                    <xsl:with-param name="label-1" select="$label-1"/>
                                    <xsl:with-param name="attr-1" select="$attr-1"/>
                                    <xsl:with-param name="label-2" select="$label-2"/>
                                    <xsl:with-param name="attr-2" select="$attr-2"/>
                                    <xsl:with-param name="label-3" select="$label-3"/>
                                    <xsl:with-param name="attr-3" select="$attr-3"/>
                                    <xsl:with-param name="label-4" select="$label-4"/>
                                    <xsl:with-param name="attr-4" select="$attr-4"/>
                                </xsl:call-template>
                            </xsl:for-each>
                        </fo:table-body>
                    </fo:table>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="no-data-block"/>
                </xsl:otherwise>
            </xsl:choose>
        </fo:block>
    </xsl:template>

    <!-- One row of a data section, arranged either way. The context node is the row. -->
    <xsl:template name="data-row">
        <xsl:param name="stacked"/>
        <xsl:param name="indent"/>
        <xsl:param name="lead-heading"/>
        <xsl:param name="label-1"/>
        <xsl:param name="attr-1"/>
        <xsl:param name="label-2"/>
        <xsl:param name="attr-2"/>
        <xsl:param name="label-3"/>
        <xsl:param name="attr-3"/>
        <xsl:param name="label-4"/>
        <xsl:param name="attr-4"/>
        <fo:table-row>
            <xsl:choose>
                <xsl:when test="$stacked">
                    <fo:table-cell padding="1mm 2mm" border-bottom="0.25pt solid #eeeeee">
                        <xsl:if test="$indent != ''">
                            <xsl:attribute name="padding-left">
                                <xsl:value-of select="$indent"/>
                            </xsl:attribute>
                        </xsl:if>
                        <xsl:choose>
                            <xsl:when test="$lead-heading">
                                <fo:block font-size="9pt" font-weight="bold"
                                    font-family="{$value-font-family}">
                                    <xsl:call-template name="data-value">
                                        <xsl:with-param name="attrs" select="$attr-1"/>
                                    </xsl:call-template>
                                </fo:block>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:call-template name="data-stacked-line">
                                    <xsl:with-param name="label" select="$label-1"/>
                                    <xsl:with-param name="attrs" select="$attr-1"/>
                                </xsl:call-template>
                            </xsl:otherwise>
                        </xsl:choose>
                        <xsl:call-template name="data-stacked-line">
                            <xsl:with-param name="label" select="$label-2"/>
                            <xsl:with-param name="attrs" select="$attr-2"/>
                        </xsl:call-template>
                        <xsl:call-template name="data-stacked-line">
                            <xsl:with-param name="label" select="$label-3"/>
                            <xsl:with-param name="attrs" select="$attr-3"/>
                        </xsl:call-template>
                        <xsl:call-template name="data-stacked-line">
                            <xsl:with-param name="label" select="$label-4"/>
                            <xsl:with-param name="attrs" select="$attr-4"/>
                        </xsl:call-template>
                    </fo:table-cell>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="data-cell">
                        <xsl:with-param name="attrs" select="$attr-1"/>
                        <xsl:with-param name="indent" select="$indent"/>
                    </xsl:call-template>
                    <xsl:call-template name="data-cell">
                        <xsl:with-param name="attrs" select="$attr-2"/>
                    </xsl:call-template>
                    <xsl:call-template name="data-cell">
                        <xsl:with-param name="attrs" select="$attr-3"/>
                    </xsl:call-template>
                    <xsl:call-template name="data-cell">
                        <xsl:with-param name="attrs" select="$attr-4"/>
                    </xsl:call-template>
                </xsl:otherwise>
            </xsl:choose>
        </fo:table-row>
    </xsl:template>

    <!-- Column heading. Sits in fo:table-header, so it repeats on every page the
         table spans. -->
    <xsl:template name="data-header-cell">
        <xsl:param name="label"/>
        <xsl:param name="attrs"/>
        <xsl:if test="$attrs != ''">
            <fo:table-cell padding="1mm 2mm" border-bottom="0.5pt solid #cccccc">
                <fo:block font-size="8pt" font-weight="bold" color="#444444">
                    <xsl:value-of select="$label"/>
                </fo:block>
            </fo:table-cell>
        </xsl:if>
    </xsl:template>

    <!-- One value cell of the table arrangement. -->
    <xsl:template name="data-cell">
        <xsl:param name="attrs"/>
        <xsl:param name="indent"/>
        <xsl:if test="$attrs != ''">
            <fo:table-cell padding="1mm 2mm">
                <xsl:if test="$indent != ''">
                    <xsl:attribute name="padding-left">
                        <xsl:value-of select="$indent"/>
                    </xsl:attribute>
                </xsl:if>
                <fo:block font-size="9pt">
                    <xsl:call-template name="data-value">
                        <xsl:with-param name="attrs" select="$attrs"/>
                    </xsl:call-template>
                </fo:block>
            </fo:table-cell>
        </xsl:if>
    </xsl:template>

    <!-- One "Label: value" line of the stacked arrangement, carrying the same label the
         table arrangement would have put in its column heading. -->
    <xsl:template name="data-stacked-line">
        <xsl:param name="label"/>
        <xsl:param name="attrs"/>
        <xsl:if test="$attrs != ''">
            <fo:block font-size="9pt">
                <fo:inline color="#444444">
                    <xsl:value-of select="$label"/>
                    <xsl:text>: </xsl:text>
                </fo:inline>
                <xsl:call-template name="data-value">
                    <xsl:with-param name="attrs" select="$attrs"/>
                </xsl:call-template>
            </fo:block>
        </xsl:if>
    </xsl:template>

    <!--
         Reads a column's value off the row. A column may name more than one attribute —
         a lab result reads "value units" — in which case each further non-empty
         attribute is appended after a space.
    -->
    <xsl:template name="data-value">
        <xsl:param name="attrs"/>
        <xsl:param name="first" select="true()"/>
        <xsl:variable name="head" select="substring-before(concat($attrs, ' '), ' ')"/>
        <xsl:variable name="tail" select="substring-after($attrs, ' ')"/>
        <xsl:variable name="value" select="@*[name() = $head]"/>
        <xsl:if test="not($first) and $value != ''">
            <xsl:text> </xsl:text>
        </xsl:if>
        <xsl:value-of select="$value"/>
        <xsl:if test="$tail != ''">
            <xsl:call-template name="data-value">
                <xsl:with-param name="attrs" select="$tail"/>
                <xsl:with-param name="first" select="false()"/>
            </xsl:call-template>
        </xsl:if>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Diagnoses — name, certainty, rank.
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="diagnoses">
        <xsl:call-template name="data-section">
            <xsl:with-param name="heading" select="@heading"/>
            <xsl:with-param name="rows" select="diagnosis"/>
            <xsl:with-param name="standard-columns">
                <fo:table-column column-width="60%"/>
                <fo:table-column column-width="25%"/>
                <fo:table-column column-width="15%"/>
            </xsl:with-param>
            <xsl:with-param name="label-1" select="@col-name"/>
            <xsl:with-param name="attr-1" select="'name'"/>
            <xsl:with-param name="label-2" select="@col-certainty"/>
            <xsl:with-param name="attr-2" select="'certainty'"/>
            <xsl:with-param name="label-3" select="@col-rank"/>
            <xsl:with-param name="attr-3" select="'rank'"/>
        </xsl:call-template>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Conditions — name and onset. Two columns still sit side by side on narrow paper,
         so this one stacks only on compact.
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="conditions">
        <xsl:call-template name="data-section">
            <xsl:with-param name="heading" select="@heading"/>
            <xsl:with-param name="rows" select="condition"/>
            <xsl:with-param name="stack-from" select="'compact'"/>
            <xsl:with-param name="standard-columns">
                <fo:table-column column-width="65%"/>
                <fo:table-column column-width="35%"/>
            </xsl:with-param>
            <xsl:with-param name="label-1" select="@col-name"/>
            <xsl:with-param name="attr-1" select="'name'"/>
            <xsl:with-param name="label-2" select="@col-onset"/>
            <xsl:with-param name="attr-2" select="'onset'"/>
        </xsl:call-template>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Lab results — test, result, reference range, flag. Standalone results are <lab>
         children; grouped panels are <lab-group heading="…"> wrappers whose heading spans
         the row, followed by their member <lab> rows.
         Stacked, all four keep their labels: a bare value beside a bare range is not a
         result a patient can read, and the flag is the safety signal.
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="labResults">
        <xsl:call-template name="data-section">
            <xsl:with-param name="heading" select="@heading"/>
            <xsl:with-param name="rows" select="lab"/>
            <xsl:with-param name="groups" select="lab-group"/>
            <xsl:with-param name="lead-heading" select="false()"/>
            <xsl:with-param name="row-indent" select="'2mm'"/>
            <xsl:with-param name="group-row-indent" select="'4mm'"/>
            <xsl:with-param name="standard-columns">
                <fo:table-column column-width="40%"/>
                <fo:table-column column-width="22%"/>
                <fo:table-column column-width="26%"/>
                <fo:table-column column-width="12%"/>
            </xsl:with-param>
            <xsl:with-param name="label-1" select="@col-test"/>
            <xsl:with-param name="attr-1" select="'name'"/>
            <xsl:with-param name="label-2" select="@col-result"/>
            <xsl:with-param name="attr-2" select="'value units'"/>
            <xsl:with-param name="label-3" select="@col-range"/>
            <xsl:with-param name="attr-3" select="'range'"/>
            <xsl:with-param name="label-4" select="@col-flag"/>
            <xsl:with-param name="attr-4" select="'flag'"/>
        </xsl:call-template>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Allergies — allergen, severity, reactions. Severity keeps its own labelled line
         rather than being folded into the allergen: it decides how urgently the rest of
         the entry matters.
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="allergies">
        <xsl:call-template name="data-section">
            <xsl:with-param name="heading" select="@heading"/>
            <xsl:with-param name="rows" select="allergy"/>
            <xsl:with-param name="standard-columns">
                <fo:table-column column-width="35%"/>
                <fo:table-column column-width="20%"/>
                <fo:table-column column-width="45%"/>
            </xsl:with-param>
            <xsl:with-param name="label-1" select="@col-allergen"/>
            <xsl:with-param name="attr-1" select="'allergen'"/>
            <xsl:with-param name="label-2" select="@col-severity"/>
            <xsl:with-param name="attr-2" select="'severity'"/>
            <xsl:with-param name="label-3" select="@col-reactions"/>
            <xsl:with-param name="attr-3" select="'reactions'"/>
        </xsl:call-template>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Medications — medication, dosing, duration, start date. The drug name leads the
         stacked block, with dosing as the first labelled line under it.
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="medications">
        <xsl:call-template name="data-section">
            <xsl:with-param name="heading" select="@heading"/>
            <xsl:with-param name="rows" select="medication"/>
            <xsl:with-param name="standard-columns">
                <fo:table-column column-width="32%"/>
                <fo:table-column column-width="34%"/>
                <fo:table-column column-width="16%"/>
                <fo:table-column column-width="18%"/>
            </xsl:with-param>
            <xsl:with-param name="label-1" select="@col-name"/>
            <xsl:with-param name="attr-1" select="'name'"/>
            <xsl:with-param name="label-2" select="@col-dosing"/>
            <xsl:with-param name="attr-2" select="'dosing'"/>
            <xsl:with-param name="label-3" select="@col-duration"/>
            <xsl:with-param name="attr-3" select="'duration'"/>
            <xsl:with-param name="label-4" select="@col-start"/>
            <xsl:with-param name="attr-4" select="'start'"/>
        </xsl:call-template>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Visit notes: one block per note, oldest first, each with a
         provenance line (encounter datetime — provider) above the narrative.
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="visitNotes">
        <fo:block font-family="{$label-font-family}" margin-bottom="2.5mm">
            <fo:block font-size="11pt" font-weight="bold" font-family="{$value-font-family}"
                margin-bottom="1mm" color="{$section-heading-color}"
                border-bottom="0.5pt solid #cccccc" padding-bottom="0.5mm">
                <xsl:value-of select="@heading"/>
            </fo:block>
            <xsl:choose>
                <xsl:when test="note">
                    <xsl:for-each select="note">
                        <fo:block margin-bottom="2mm">
                            <fo:block font-size="8pt" color="#666666" font-style="italic">
                                <xsl:value-of select="@datetime"/>
                                <xsl:text> — </xsl:text>
                                <xsl:value-of select="@provider"/>
                            </fo:block>
                            <fo:block font-size="9pt" linefeed-treatment="preserve">
                                <xsl:value-of select="."/>
                            </fo:block>
                        </fo:block>
                    </xsl:for-each>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="no-data-block"/>
                </xsl:otherwise>
            </xsl:choose>
        </fo:block>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Billing (stub)
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="billing">
        <fo:block font-family="{$label-font-family}" margin-bottom="2.5mm">
            <fo:block font-size="11pt" font-weight="bold" font-family="{$value-font-family}"
                margin-bottom="1mm" color="{$section-heading-color}"
                border-bottom="0.5pt solid #cccccc" padding-bottom="0.5mm">
                <xsl:value-of select="@heading"/>
            </fo:block>
            <xsl:choose>
                <xsl:when test="item">
                    <!-- Data will be rendered here during implementation -->
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="no-data-block"/>
                </xsl:otherwise>
            </xsl:choose>
        </fo:block>
    </xsl:template>

    <!-- The footer element renders in the xsl-region-after static content on every
         page (see the root template), never in the body flow — skip it there. -->
    <xsl:template match="footer"/>

    <!-- Sections contributed by downstream modules without a template in this
         stylesheet are skipped rather than leaking raw text into the PDF. -->
    <xsl:template match="*" priority="-1"/>

    <!-- ═══════════════════════════════════════════════════
         Footer: facility name and "Page N of M" on the first row;
         printed-by, timestamp and system ID on the second.
         Rendered as fo:static-content in xsl-region-after, so it appears on
         every page. The page total comes from an fo:page-number-citation
         against the "visit-summary-end" block at the end of the flow.
         Label text comes from footer/@lbl-* attributes set by the renderer.
         ═══════════════════════════════════════════════════ -->
    <xsl:template name="footer">
        <fo:block font-family="{$label-font-family}" font-size="7pt" color="#444444"
            border-top="0.5pt solid #cccccc" padding-top="1mm">
            <fo:table width="100%" table-layout="fixed">
                <fo:table-column column-width="40%"/>
                <fo:table-column column-width="35%"/>
                <fo:table-column column-width="25%"/>
                <fo:table-body>
                    <fo:table-row>
                        <fo:table-cell number-columns-spanned="2">
                            <fo:block>
                                <xsl:value-of select="footer/facilityName"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell>
                            <fo:block text-align="right">
                                <xsl:value-of select="footer/@lbl-page"/>
                                <xsl:text> </xsl:text>
                                <fo:page-number/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="footer/@lbl-of"/>
                                <xsl:text> </xsl:text>
                                <fo:page-number-citation ref-id="visit-summary-end"/>
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                    <fo:table-row>
                        <fo:table-cell>
                            <fo:block>
                                <xsl:value-of select="footer/@lbl-printed-by"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="footer/printedBy"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell>
                            <fo:block>
                                <xsl:value-of select="footer/timestamp"/>
                            </fo:block>
                        </fo:table-cell>
                        <fo:table-cell>
                            <fo:block text-align="right">
                                <xsl:value-of select="footer/@lbl-system-id"/>
                                <xsl:text> </xsl:text>
                                <xsl:value-of select="footer/systemId"/>
                            </fo:block>
                        </fo:table-cell>
                    </fo:table-row>
                </fo:table-body>
            </fo:table>
        </fo:block>
    </xsl:template>
    
    <!-- ═══════════════════════════════════════════════════
         No-data state — shared by every section whose element has no rows.
         Deliberately distinct from the red section-error banner (grey text,
         dashed border) so a clinician can tell "nothing was recorded" apart
         from "the data could not be retrieved" on the printed page.
         Label text comes from /visitSummary/@lbl-no-data set by the renderer.
         ═══════════════════════════════════════════════════ -->
    <xsl:template name="no-data-block">
        <fo:block font-size="9pt" color="#777777" font-style="italic"
                  padding="3pt" border="0.5pt dashed #bbbbbb"
                  background-color="#fafafa">
            <xsl:value-of select="/visitSummary/@lbl-no-data"/>
        </fo:block>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Section error fallback
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="section-error">
        <fo:block font-size="9pt" font-style="italic" color="#CC0000"
                  space-before="6pt" space-after="6pt"
                  padding="4pt" border="0.5pt solid #CC0000"
                  background-color="#FFF0F0">
            <!-- Bundled Plex Sans Arabic has no U+26A0, so a warning-sign ⚠ glyph renders as "#" -->
            <fo:inline font-weight="bold">! </fo:inline>
            <xsl:value-of select="@key"/>
            <xsl:text>: </xsl:text>
            <xsl:value-of select="@message"/>
        </fo:block>
    </xsl:template>

    <!-- ═══════════════════════════════════════════════════
         Section notice — partial data loaded (non-fatal warning)
         ═══════════════════════════════════════════════════ -->
    <xsl:template match="section-notice">
        <fo:block font-size="9pt" font-style="italic" color="#B8860B"
                  space-before="6pt" space-after="6pt"
                  padding="4pt" border="0.5pt solid #B8860B"
                  background-color="#FFF8E1">
            <!-- Bundled Plex Sans Arabic has no U+26A0, so a warning-sign ⚠ glyph renders as "#" -->
            <fo:inline font-weight="bold">! </fo:inline>
            <xsl:value-of select="@message"/>
        </fo:block>
    </xsl:template>

</xsl:stylesheet>
