/*
 * $Id$
 *
 * Copyright 2013 Valentyn Kolesnikov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.xmltopdf;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.List;

import com.mycila.xmltool.XMLDoc;
import com.mycila.xmltool.XMLTag;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.resource.loader.StringResourceLoader;
import org.apache.velocity.runtime.resource.util.StringResourceRepository;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.export.JRRtfExporter;
import net.sf.jasperreports.engine.export.JRXlsExporter;
import net.sf.jasperreports.engine.export.JRXlsExporterParameter;

/**.
 * @author Valentyn Kolesnikov
 * @version $Revision$ $Date$
 */
public class JasperPdfGenerator {
    private static final String USAGE = "Usage: java -jar xmltopdf.jar template.jrxml data.xml";
    private XMLTag xmlTag;

    /**.*/
    public enum DocType {
        PDF, RTF, XLS;
    }

    public JasperPdfGenerator() {
    }

    static {
        org.apache.log4j.BasicConfigurator.configure();
    }

    private void createDocument(List<String> templateNames, List<String> xmlFileNames, ByteArrayOutputStream os, DocType docType) {
        List<JasperPrint> jasperPrints = new ArrayList<JasperPrint>();
        InputStream fileIs = null;
        InputStream stringIs = null;
        if (!xmlFileNames.isEmpty()) {
            xmlTag = XMLDoc.from(new File(xmlFileNames.get(0)), true);
        }
        try {
            for (String templateName : templateNames) {
                try {
                    fileIs = new FileInputStream(templateNames.get(0));
                    String contents = applyVelocityTemplate(IOUtils.toString(fileIs, "UTF-8"));
                    stringIs = IOUtils.toInputStream(contents, "UTF-8");
                    JasperReport jasperReport = JasperCompileManager.compileReport(stringIs);
                    jasperPrints.add(JasperFillManager.fillReport(
                        jasperReport, new HashMap(), new JREmptyDataSource()));
                } finally {
                    IOUtils.closeQuietly(fileIs);
                    IOUtils.closeQuietly(stringIs);
                }
            }
            JasperPrint jasperPrint = jasperPrints.get(0);
            for (int index = 1; index < jasperPrints.size(); index += 1) {
                List<JRPrintPage> pages = jasperPrints.get(index).getPages();
                for (JRPrintPage page : pages) {
                    jasperPrint.addPage(page);
                }
            }
            switch (docType) {
                case PDF:
                    JasperExportManager.exportReportToPdfStream(jasperPrint, os);
                    break;
                case RTF:
                    JRRtfExporter rtfExporter = new JRRtfExporter();
                    rtfExporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
                    rtfExporter.setParameter(JRExporterParameter.OUTPUT_STREAM, os);
                    rtfExporter.exportReport();
                    break;
                case XLS:
                    JRXlsExporter xlsExporter = new JRXlsExporter();
                    xlsExporter.setParameter(JRXlsExporterParameter.JASPER_PRINT, jasperPrint);
                    xlsExporter.setParameter(JRXlsExporterParameter.OUTPUT_STREAM, os);
                    xlsExporter.setParameter(JRXlsExporterParameter.IS_ONE_PAGE_PER_SHEET, Boolean.TRUE);
                    xlsExporter.setParameter(JRXlsExporterParameter.IS_AUTO_DETECT_CELL_TYPE, Boolean.TRUE);
                    xlsExporter.setParameter(JRXlsExporterParameter.IS_WHITE_PAGE_BACKGROUND, Boolean.FALSE);
                    xlsExporter.setParameter(JRXlsExporterParameter.IS_REMOVE_EMPTY_SPACE_BETWEEN_ROWS, Boolean.TRUE);
                    xlsExporter.exportReport();
                    break;
                default:
                    break;
            }
        } catch (Exception ex) {
            LOG.error(this, ex, ex.getMessage());
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    private String applyVelocityTemplate(String templateData) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "string");
        properties.setProperty("string.resource.loader.class", "org.apache.velocity.runtime.resource.loader.StringResourceLoader");
        properties.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogSystem");
        properties.setProperty("userdirective",
                "com.github.xmltopdf.MoneyUAHDirective,"
                + "com.github.xmltopdf.MoneyToStrDirective,"
                + "com.github.xmltopdf.DateDirective");
        Velocity.init(properties);

        StringResourceRepository repo = StringResourceLoader.getRepository();
        repo.putStringResource("template", templateData);
        Template template = Velocity.getTemplate("template", "UTF-8");
        StringWriter writer = new StringWriter();
        VelocityContext context = new VelocityContext();
        context.put("xml", xmlTag);
        template.merge(context, writer);
        writer.flush();
        writer.close();
        return writer.toString();
    }

    /**.*/
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
           LOG.info(null, USAGE);
           return;
        }
        List<String> templates = new ArrayList<String>();
        List<String> xmls = new ArrayList<String>();
        for (String arg : args) {
            if (arg.endsWith(".jrxml")) {
                templates.add(arg);
            } else if (arg.endsWith(".xml")) {
                xmls.add(arg);
            }
        }
        if (templates.isEmpty()) {
           LOG.info(null, USAGE);
           return;
        }
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new JasperPdfGenerator().createDocument(templates, xmls, os, DocType.PDF);
        os.writeTo(new FileOutputStream(templates.get(0).replaceFirst("\\.jrxml$", ".pdf")));
    }
}