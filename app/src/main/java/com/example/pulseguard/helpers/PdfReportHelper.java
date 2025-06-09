package com.example.pulseguard.helpers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.location.Location;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PdfReportHelper {

    private static final String TAG = "PdfReportHelper";

    // Page and margin dimensions (A4 size)
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 50;

    // Text sizes
    private static final int TITLE_SIZE = 28;
    private static final int SECTION_TITLE_SIZE = 20;
    private static final int LABEL_SIZE = 14;
    private static final int VALUE_SIZE = 14;
    private static final int FOOTER_SIZE = 11;

    private final Context context;

    public PdfReportHelper(Context context) {
        this.context = context;
    }

    /**
     * Creates a health report PDF file with user and health data, optionally including location.
     *
     * @param reportTitle Title of the report (e.g., "PulseGuard Health Report")
     * @param userInfo    UserInfo object containing user details
     * @param healthData  HealthData object containing health metrics
     * @param location    Location data, can be null if unavailable
     * @return File object pointing to the generated PDF file
     * @throws IOException if file creation or writing fails
     */
    public File createHealthReport(String reportTitle, UserInfo userInfo, HealthData healthData, Location location) throws IOException {
        PdfDocument document = new PdfDocument();

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        int yPos = MARGIN;

        // Draw header (title + logo + timestamp)
        yPos = drawHeader(canvas, reportTitle, yPos);

        // Draw user info section
        yPos += 20;
        yPos = drawUserInfoSection(canvas, userInfo, yPos);

        // Draw separator line
        yPos += 15;
        yPos = drawSeparator(canvas, yPos);

        // Draw health metrics section
        yPos += 15;
        yPos = drawHealthDataSection(canvas, healthData, yPos);

        // Draw separator line
        yPos += 15;
        yPos = drawSeparator(canvas, yPos);

        // Draw location section or location unavailable message
        yPos += 15;
        if (location != null) {
            yPos = drawLocationSection(canvas, location, yPos);
        } else {
            yPos = drawLocationUnavailableSection(canvas, yPos);
        }

        // Draw footer (page info, copyright)
        drawFooter(canvas, 1);

        document.finishPage(page);

        // Create file in app documents folder
        File pdfFile = createOutputFile();

        try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
            document.writeTo(fos);
            Log.i(TAG, "PDF report created successfully: " + pdfFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error writing PDF file", e);
            throw e;
        } finally {
            document.close();
        }

        return pdfFile;
    }

    /**
     * Draws the header with background color, logo circle, title text, and timestamp.
     * Returns the vertical position after header content.
     */
    private int drawHeader(Canvas canvas, String title, int startY) {
        Paint paint = new Paint();

        // Draw background rectangle for header
        paint.setColor(Color.parseColor("#3F51B5")); // Indigo 500
        canvas.drawRect(0, 0, PAGE_WIDTH, startY + 50, paint);

        // Draw simple circular logo (white)
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        int logoRadius = 20;
        int logoX = MARGIN + logoRadius;
        int logoY = startY + 25;
        canvas.drawCircle(logoX, logoY, logoRadius, paint);

        // Draw title text next to logo
        paint.setColor(Color.WHITE);
        paint.setTextSize(TITLE_SIZE);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD));
        float titleX = logoX + logoRadius + 15;
        float titleY = logoY + (TITLE_SIZE / 3f);
        canvas.drawText(title, titleX, titleY, paint);

        // Draw timestamp on right side
        paint.setTextSize(12);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setAlpha(160);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        float timestampX = PAGE_WIDTH - MARGIN - paint.measureText("Generated: " + timestamp);
        canvas.drawText("Generated: " + timestamp, timestampX, titleY, paint);

        return startY + 50;  // height of header area
    }

    /**
     * Draws the user information section with labels and values.
     * Returns vertical position after section.
     */
    private int drawUserInfoSection(Canvas canvas, UserInfo userInfo, int startY) {
        Paint labelPaint = createPaint(Color.parseColor("#303F9F"), LABEL_SIZE, Typeface.BOLD);
        Paint valuePaint = createPaint(Color.BLACK, VALUE_SIZE, Typeface.NORMAL);

        // Section title
        canvas.drawText("User Information", MARGIN, startY, labelPaint);

        int y = startY + LABEL_SIZE + 10;

        y = drawLabelValue(canvas, "Name:", safeText(userInfo.getName()), y, labelPaint, valuePaint);
        y = drawLabelValue(canvas, "Email:", safeText(userInfo.getEmail()), y, labelPaint, valuePaint);
        y = drawLabelValue(canvas, "Date of Birth:", safeText(userInfo.getDob()), y, labelPaint, valuePaint);
        y = drawLabelValue(canvas, "Phone Number:", safeText(userInfo.getPhone()), y, labelPaint, valuePaint);
        y = drawLabelValue(canvas, "Address:", safeText(userInfo.getAddress()), y, labelPaint, valuePaint);

        return y;
    }

    /**
     * Draws health data section with steps, calories, heart rate.
     * Returns vertical position after section.
     */
    private int drawHealthDataSection(Canvas canvas, HealthData data, int startY) {
        Paint sectionTitlePaint = createPaint(Color.parseColor("#303F9F"), SECTION_TITLE_SIZE, Typeface.BOLD);
        Paint labelPaint = createPaint(Color.DKGRAY, LABEL_SIZE, Typeface.BOLD);
        Paint valuePaint = createPaint(Color.BLACK, VALUE_SIZE, Typeface.NORMAL);

        canvas.drawText("Health Metrics", MARGIN, startY, sectionTitlePaint);

        int y = startY + SECTION_TITLE_SIZE + 10;

        y = drawLabelValue(canvas, "Steps:", String.valueOf(data.getSteps()), y, labelPaint, valuePaint);
        y = drawLabelValue(canvas, "Calories:", String.format(Locale.getDefault(), "%.1f kcal", data.getCalories()), y, labelPaint, valuePaint);
        y = drawLabelValue(canvas, "Heart Rate:", String.format(Locale.getDefault(), "%.1f bpm", data.getHeartRate()), y, labelPaint, valuePaint);

        return y;
    }

    /**
     * Draws location data section with coordinates, accuracy, and Google Maps link.
     * Returns vertical position after section.
     */
    private int drawLocationSection(Canvas canvas, Location location, int startY) {
        Paint sectionTitlePaint = createPaint(Color.parseColor("#303F9F"), SECTION_TITLE_SIZE, Typeface.BOLD);
        Paint labelPaint = createPaint(Color.DKGRAY, LABEL_SIZE, Typeface.BOLD);
        Paint valuePaint = createPaint(Color.BLACK, VALUE_SIZE, Typeface.NORMAL);

        canvas.drawText("Location Data", MARGIN, startY, sectionTitlePaint);

        int y = startY + SECTION_TITLE_SIZE + 10;

        y = drawLabelValue(canvas, "Coordinates:", String.format(Locale.getDefault(), "%.6f, %.6f", location.getLatitude(), location.getLongitude()), y, labelPaint, valuePaint);
        y = drawLabelValue(canvas, "Accuracy:", String.format(Locale.getDefault(), "%.1f meters", location.getAccuracy()), y, labelPaint, valuePaint);

        String mapsUrl = "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
        y = drawLabelValue(canvas, "Google Maps:", mapsUrl, y, labelPaint, valuePaint);

        return y;
    }

    /**
     * Draws a message indicating location data is unavailable.
     * Returns vertical position after message.
     */
    private int drawLocationUnavailableSection(Canvas canvas, int startY) {
        Paint sectionTitlePaint = createPaint(Color.parseColor("#303F9F"), SECTION_TITLE_SIZE, Typeface.BOLD);
        Paint valuePaint = createPaint(Color.RED, VALUE_SIZE, Typeface.NORMAL);

        canvas.drawText("Location Data", MARGIN, startY, sectionTitlePaint);

        int y = startY + SECTION_TITLE_SIZE + 10;

        canvas.drawText("Location: Not available (please enable location)", MARGIN, y, valuePaint);

        return y + VALUE_SIZE + 10;
    }

    /**
     * Draws a horizontal separator line.
     * Returns the y position passed in.
     */
    private int drawSeparator(Canvas canvas, int y) {
        Paint paint = new Paint();
        paint.setColor(Color.LTGRAY);
        paint.setStrokeWidth(2);
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint);
        return y;
    }

    /**
     * Draws footer text including confidentiality note and page number.
     */
    private void drawFooter(Canvas canvas, int pageNumber) {
        Paint paint = new Paint();
        paint.setColor(Color.DKGRAY);
        paint.setTextSize(FOOTER_SIZE);
        paint.setTextAlign(Paint.Align.CENTER);

        String footerText = "Generated by PulseGuard - Confidential Health Data";
        float centerX = PAGE_WIDTH / 2f;
        float yFooter = PAGE_HEIGHT - 25;

        canvas.drawText(footerText, centerX, yFooter, paint);
        canvas.drawText("Page " + pageNumber, centerX, yFooter + FOOTER_SIZE + 5, paint);
    }

    /**
     * Draws a label and its corresponding value on the same line.
     * Returns the next vertical position for following text.
     */
    private int drawLabelValue(Canvas canvas, String label, String value, int y, Paint labelPaint, Paint valuePaint) {
        canvas.drawText(label, MARGIN, y, labelPaint);

        float labelWidth = labelPaint.measureText(label);
        float valueX = MARGIN + labelWidth + 10;

        // Clip long text for values if needed, or wrap logic can be added later
        String clippedValue = value;
        float maxWidth = PAGE_WIDTH - MARGIN - valueX;
        if (valuePaint.measureText(value) > maxWidth) {
            clippedValue = clipText(value, valuePaint, maxWidth);
        }

        canvas.drawText(clippedValue, valueX, y, valuePaint);

        return y + LABEL_SIZE + 12;
    }

    /**
     * Clips text to fit max width with ellipsis.
     */
    private String clipText(String text, Paint paint, float maxWidth) {
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int len = text.length();
        while (len > 0 && paint.measureText(text.substring(0, len) + ellipsis) > maxWidth) {
            len--;
        }
        return text.substring(0, len) + ellipsis;
    }

    /**
     * Creates a configured Paint object with given color, size, and style.
     */
    private Paint createPaint(int color, int textSize, int style) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(textSize);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, style));
        return paint;
    }

    /**
     * Ensures text is not null or empty, returns "N/A" if so.
     */
    private String safeText(String text) {
        return (text == null || text.trim().isEmpty()) ? "N/A" : text.trim();
    }

    /**
     * Creates the output file in the app's Documents folder with a timestamped filename.
     */
    private File createOutputFile() throws IOException {
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) {
            throw new IOException("Unable to access external Documents directory.");
        }

        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create Documents directory.");
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String filename = "PulseGuard_Report_" + timestamp + ".pdf";

        return new File(directory, filename);
    }

    /**
     * Returns a content URI for the given file using FileProvider.
     * Make sure your app manifest and provider paths are configured correctly.
     */
    public android.net.Uri getPdfUri(File file) {
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".provider",
                file
        );
    }

    // === Data Classes ===

    /**
     * Struct to hold health metrics.
     */
    public static class HealthData {
        private final int steps;
        private final float calories;
        private final float heartRate;

        public HealthData(int steps, float calories, float heartRate) {
            this.steps = steps;
            this.calories = calories;
            this.heartRate = heartRate;
        }

        public int getSteps() {
            return steps;
        }

        public float getCalories() {
            return calories;
        }

        public float getHeartRate() {
            return heartRate;
        }
    }

    /**
     * Struct to hold user information.
     */
    public static class UserInfo {
        private final String name;
        private final String email;
        private final String dob;
        private final String phone;
        private final String address;

        public UserInfo(String name, String email, String dob, String phone, String address) {
            this.name = safe(name);
            this.email = safe(email);
            this.dob = safe(dob);
            this.phone = safe(phone);
            this.address = safe(address);
        }

        private static String safe(String s) {
            return (s == null || s.trim().isEmpty()) ? "N/A" : s.trim();
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getDob() {
            return dob;
        }

        public String getPhone() {
            return phone;
        }

        public String getAddress() {
            return address;
        }
    }
}
