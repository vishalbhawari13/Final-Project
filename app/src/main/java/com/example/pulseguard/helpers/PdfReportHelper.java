package com.example.pulseguard.helpers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.os.Environment;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PdfReportHelper {

    private final Context context;

    public PdfReportHelper(Context context) {
        this.context = context;
    }

    /**
     * Generates a PDF report with the specified title and content.
     * Saves the PDF in the app-specific Documents directory with a timestamped filename.
     *
     * @param reportTitle   The title of the report.
     * @param reportContent The multiline content of the report.
     * @return The generated PDF file.
     * @throws IOException If an error occurs during file writing.
     */
    public File createPdfReport(String reportTitle, String reportContent) throws IOException {
        PdfDocument document = new PdfDocument();

        // A4 size approximately in points (595 x 842)
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        Paint paint = new Paint();

        // Draw title
        paint.setTextSize(22f);
        paint.setFakeBoldText(true);
        canvas.drawText(reportTitle, 80, 50, paint);

        // Draw content lines
        paint.setTextSize(16f);
        paint.setFakeBoldText(false);

        int xStart = 80;
        int yStart = 100;
        int lineHeight = 30;
        int maxLineWidth = pageInfo.getPageWidth() - xStart * 2;

        // Split content into lines and wrap if too long
        for (String line : reportContent.split("\n")) {
            for (String wrappedLine : wrapText(line, paint, maxLineWidth)) {
                canvas.drawText(wrappedLine, xStart, yStart, paint);
                yStart += lineHeight;
            }
        }

        document.finishPage(page);

        // Create file with timestamped name
        String filename = "HealthReport_" + System.currentTimeMillis() + ".pdf";
        File pdfFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), filename);

        // Write to file with try-with-resources to ensure closure
        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            document.writeTo(fos);
        } finally {
            document.close();
        }

        return pdfFile;
    }

    /**
     * Returns a content URI for the given PDF file to be shared via FileProvider.
     *
     * @param file The PDF file.
     * @return The content URI.
     */
    public android.net.Uri getPdfUri(File file) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
    }

    /**
     * Utility method to wrap text into multiple lines if it exceeds maxWidth.
     *
     * @param text     The input text.
     * @param paint    The Paint object used for measuring text width.
     * @param maxWidth Maximum width in pixels for a line.
     * @return An array of wrapped lines.
     */
    private String[] wrapText(String text, Paint paint, int maxWidth) {
        if (paint.measureText(text) <= maxWidth) {
            return new String[]{text};
        }

        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        java.util.List<String> lines = new java.util.ArrayList<>();

        for (String word : words) {
            if (paint.measureText(line + word) <= maxWidth) {
                line.append(word).append(" ");
            } else {
                lines.add(line.toString().trim());
                line = new StringBuilder(word + " ");
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString().trim());
        }

        return lines.toArray(new String[0]);
    }
}
