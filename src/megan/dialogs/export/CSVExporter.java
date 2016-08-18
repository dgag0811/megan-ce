/*
 *  Copyright (C) 2016 Daniel H. Huson
 *
 *  (Some files contain contributions from other authors, who are then mentioned separately.)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package megan.dialogs.export;

import jloda.util.Basic;
import jloda.util.ProgramProperties;
import jloda.util.ProgressListener;
import megan.classification.ClassificationManager;
import megan.core.Director;
import megan.viewer.ClassificationViewer;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * exports Data in CSV format
 * Daniel Huson, 5.2015
 */
public class CSVExporter {

    /**
     * constructor
     *
     * @param classificationName
     */
    public static List<String> getFormats(String classificationName, boolean hasReads) {
        final List<String> formats = new LinkedList<>();

        String shortName = classificationName.toLowerCase();
        if (shortName.equals("taxonomy"))
            shortName = "taxon";

        formats.add(shortName + "Name_to_count");
        if (shortName.equals("taxon")) {
            formats.add(shortName + "Id_to_count");
            formats.add(shortName + "Rank_to_count");
        }
        formats.add(shortName + "Path_to_count");

        formats.add(shortName + "Name_to_percent");
        if (shortName.equals("taxon")) {
            formats.add(shortName + "Id_to_percent");
            formats.add(shortName + "Rank_to_percent");
        }
        formats.add(shortName + "Path_to_percent");

        formats.add(shortName + "Name_to_length");
        if (shortName.equals("taxon")) {
            formats.add(shortName + "Id_to_length");
            formats.add(shortName + "Rank_to_length");
        }
        formats.add(shortName + "Path_to_length");

        if (hasReads) {
            formats.add("readName_to_" + shortName + "Name");
            if (shortName.equals("taxon"))
                formats.add("readName_to_" + shortName + "Id");
            formats.add("readName_to_" + shortName + "Path");
            formats.add("readName_to_" + shortName + "Count");

            if (shortName.equals("taxon"))
                formats.add("readName_to_taxonMatches");

            formats.add(shortName + "Name_to_readName");
            // formats.add("readName_to_gi");
            formats.add("readName_to_refSeqIds");
            formats.add("readName_to_headers");
        }

        if (shortName.equals("taxon"))
            formats.add("ko_to_taxon");


        return formats;
    }


    /**
     * asks the user to input the desired format
     *
     * @param parent
     * @return format or null, if canceled
     */
    public static String showFormatInputDialog(Component parent, List<String> formats) {
        String previousChoice = ProgramProperties.get("CSVExporterChoice", formats.get(0));
        if (!formats.contains(previousChoice))
            previousChoice = formats.get(0);

        String choice = (String) JOptionPane.showInputDialog(parent, "Choose format", "Export read assignments to CSV file",
                JOptionPane.INFORMATION_MESSAGE, ProgramProperties.getProgramIcon(), formats.toArray(), previousChoice);

        if (choice != null)
            ProgramProperties.put("CSVExporterChoice", choice);

        return choice;
    }

    /**
     * apply the desired exporter
     *
     * @param frame
     * @param dir
     * @param file
     * @param format
     */
    public static int apply(Component frame, Director dir, ProgressListener progressListener, File file, String format, char separator, boolean reportSummarized) throws IOException {
        progressListener.setTasks("Export in CSV format", "Initializing");

        final Collection<String> cNames = ClassificationManager.getAllSupportedClassifications();
        int count = 0;

        if (format.equalsIgnoreCase("taxonName_to_count") || format.equalsIgnoreCase("taxonId_to_count") || format.equalsIgnoreCase("taxonRank_to_count") || format.equalsIgnoreCase("taxonPath_to_count")) {
            count = CSVExportTaxonomy.exportTaxon2Counts(format, dir, file, separator, reportSummarized, progressListener);
        } else if (format.equalsIgnoreCase("taxonName_to_readName") || format.equalsIgnoreCase("taxonId_to_ReadName") || format.equalsIgnoreCase("taxonPath_to_readName")) {
            count = CSVExportTaxonomy.exportTaxon2ReadNames(format, dir, file, separator, progressListener);
        } else if (format.equalsIgnoreCase("taxonName_to_readName") || format.equalsIgnoreCase("taxonId_to_readName") || format.equalsIgnoreCase("taxonPath_to_readName")) {
            count = CSVExportTaxonomy.exportTaxon2ReadIds(format, dir, file, separator, progressListener);
        } else if (format.equalsIgnoreCase("taxonName_to_length") || format.equalsIgnoreCase("taxonId_to_length") || format.equalsIgnoreCase("taxonRank_to_count") || format.equalsIgnoreCase("taxonPath_to_length")) {
            count = CSVExportTaxonomy.exportTaxon2TotalLength(format, dir, file, separator, progressListener);
        } else if (format.equalsIgnoreCase("taxonName_to_percent") || format.equalsIgnoreCase("taxonId_to_percent") || format.equalsIgnoreCase("taxonRank_to_count") || format.equalsIgnoreCase("taxonPath_to_percent")) {
            count = CSVExportFViewer.exportName2Percent(format, dir.getMainViewer(), file, separator, true, progressListener);
        } else if (format.equalsIgnoreCase("readName_to_taxonName") || format.equalsIgnoreCase("readName_to_taxonId") || format.equalsIgnoreCase("readName_to_taxonPath")) {
            count = CSVExportTaxonomy.exportReadName2Taxon(format, dir, file, separator, progressListener);
        } else if (format.equalsIgnoreCase("readName_to_taxonMatches")) {
            count = CSVExportTaxonomy.exportReadName2Matches(format, dir, file, separator, progressListener);
        } else if (format.equalsIgnoreCase("readName_to_refSeqIds")) {
            count = CSVExportRefSeq.exportReadName2Accession(dir.getMainViewer(), file, separator, progressListener);
        } else if (format.equalsIgnoreCase("readName_to_headers")) {
            count = CSVExportHeaders.exportReadName2Headers(dir.getMainViewer(), file, separator, progressListener);
        } else if (format.equalsIgnoreCase("reference_to_readName")) {
            count = CSVExportReference2Read.exportReference2ReadName(dir.getMainViewer(), file, separator, progressListener);
        } else if (Basic.endsWithIgnoreCase(format, "Name_to_count") || Basic.endsWithIgnoreCase(format, "Path_to_count")) {
            for (String cName : cNames) {
                if (format.startsWith(cName.toLowerCase())) {
                    ClassificationViewer viewer = (ClassificationViewer) dir.getViewerByClassName(ClassificationViewer.getClassName(cName));
                    count = CSVExportFViewer.exportName2Counts(format, viewer, file, separator, true, progressListener);
                    break;
                }
            }
        } else if (format.equalsIgnoreCase("ko_to_taxon")) {
            count = ExportKO2TaxaTable.export(format, dir, file, separator, progressListener);
        } else {
            for (String cName : cNames) {
                if (format.startsWith(cName.toLowerCase())) {
                    final ClassificationViewer viewer = (ClassificationViewer) dir.getViewerByClassName(ClassificationViewer.getClassName(cName));
                    if (Basic.endsWithIgnoreCase(format, "Name_to_count") || Basic.endsWithIgnoreCase(format, "Path_to_count")) {
                        count = CSVExportFViewer.exportName2Counts(format, viewer, file, separator, true, progressListener);
                        break;
                    } else if (Basic.endsWithIgnoreCase(format, "Name_to_readName") || Basic.endsWithIgnoreCase(format, "Path_to_readName")) {
                        count = CSVExportFViewer.exportName2ReadNames(format, viewer, file, separator, progressListener);
                        break;
                    } else if (Basic.endsWithIgnoreCase(format, "Name_to_length") || Basic.endsWithIgnoreCase(format, "Path_to_length")) {
                        count = CSVExportFViewer.exportName2TotalLength(format, viewer, file, separator, progressListener);
                        break;
                    }
                    if (Basic.endsWithIgnoreCase(format, "Name_to_percent") || Basic.endsWithIgnoreCase(format, "Id_to_percent") || Basic.endsWithIgnoreCase(format, "Path_to_perecent")) {
                        count = CSVExportFViewer.exportName2Percent(format, viewer, file, separator, true, progressListener);
                        break;
                    }
                } else if (format.contains(cName.toLowerCase())) {
                    final ClassificationViewer viewer = (ClassificationViewer) dir.getViewerByClassName(ClassificationViewer.getClassName(cName));
                    if (format.startsWith("readName_to_")) {
                        count = CSVExportFViewer.exportReadName2Name(format, viewer, file, separator, progressListener);
                        break;
                    }
                }
            }
        }
        return count;
    }
}
