package com.afmvfcc.controllers;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

/**
 * AFM VFCC Certificate Generator[For future reference]
 *
 * Coordinate system: PDF points, origin BOTTOM-LEFT.
 * "Resting on Y" = text baseline sits exactly on Y, never drops below.
 *
 * FONTS (bundled as classpath resources):
 *   Lora-Regular.ttf        elegant serif - dates, ministers, body
 *   Lora-Italic.ttf         italic serif  - blessing names (calligraphic), deceased name
 *   Caladea-Bold.ttf        scholarly bold - appreciation name
 *   Caladea-Regular.ttf     clean serif   - appreciation date
 *
 * COORDINATES (from manual analysis)(VERY IMPORTANT RAMOS!!)
 *   BAPTISM:      name  centred 180-660  y=270
 *                 date  centred 340-520  y=170
 *                 min   centred 140-320  y=133
 *   APPRECIATION: name  centred 80-580   y=270
 *                 date  centred 80-220   y=90
 *   BLESSING:     bride centred 60-400   y=375
 *                 groom centred 60-400   y=315
 *                 date  centred 140-320  y=88
 *                 photo x=520 y=180 w=220 h=260
 *   ANNOUNCEMENT: name  centred 135-280  y=305
 *                 date  centred 105-280  y=172
 *                 time  centred 105-280  y=158
 *                 venue centred 105-280  y=144
 */
public class CertificateGenerator {

    private static final String CERT_PKG = "/com/afmvfcc/certificates/";
    private static final String FONT_PKG = "/com/afmvfcc/fonts/";

    private static final File CACHE =
        new File(System.getProperty("user.home"), ".afmvfcc_certs");

    static {
        CACHE.mkdirs();
        cache(CERT_PKG, "BaptismCertificate.pdf");
        cache(CERT_PKG, "CertificateOfAppreciation.pdf");
        cache(CERT_PKG, "BlessingCertificate.pdf");
        cache(CERT_PKG, "DeathAnnouncement.pdf");
        cache(FONT_PKG, "Lora-Regular.ttf");
        cache(FONT_PKG, "Lora-Italic.ttf");
        cache(FONT_PKG, "Caladea-Bold.ttf");
        cache(FONT_PKG, "Caladea-Regular.ttf");
    }

    private static void cache(String pkg, String name) {
        File dest = new File(CACHE, name);
        if (dest.exists() && dest.length() > 0) return;
        try (InputStream in = CertificateGenerator.class.getResourceAsStream(pkg + name)) {
            if (in == null) { System.err.println("Resource not found: " + pkg + name); return; }
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ---------------------------------------------------------
    // Font loaders
    // ---------------------------------------------------------
    private static PDType0Font loraRegular(PDDocument doc) throws IOException {
        return PDType0Font.load(doc, new File(CACHE, "Lora-Regular.ttf"));
    }
    private static PDType0Font loraItalic(PDDocument doc) throws IOException {
        return PDType0Font.load(doc, new File(CACHE, "Lora-Italic.ttf"));
    }
    private static PDType0Font caladeaBold(PDDocument doc) throws IOException {
        return PDType0Font.load(doc, new File(CACHE, "Caladea-Bold.ttf"));
    }
    private static PDType0Font caladeaRegular(PDDocument doc) throws IOException {
        return PDType0Font.load(doc, new File(CACHE, "Caladea-Regular.ttf"));
    }

    // ---------------------------------------------------------
    // Draw text centred between xLeft and xRight, baseline at y
    // ---------------------------------------------------------
    private static void drawCentred(PDPageContentStream cs,
                                    PDType0Font font, float size,
                                    String text,
                                    float xLeft, float xRight, float baselineY)
            throws IOException {
        float tw     = font.getStringWidth(text) / 1000f * size;
        float startX = (xLeft + xRight) / 2f - tw / 2f;
        cs.setFont(font, size);
        cs.beginText();
        cs.newLineAtOffset(startX, baselineY);
        cs.showText(text);
        cs.endText();
    }

    // =========================================================
    // BAPTISM CERTIFICATE
    // Font: Lora-Italic for the name (distinguished, personal)
    //       Lora-Regular for date and minister
    // =========================================================
    public static void generateBaptism(String name, String date,
                                       String minister, String outPath) throws Exception {
        File tpl = new File(CACHE, "BaptismCertificate.pdf");
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBufferedFile(tpl))) {
            PDPage page = doc.getPage(0);
            PDType0Font nameFont    = loraItalic(doc);
            PDType0Font regularFont = loraRegular(doc);

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                // Name: Lora Italic 24pt, centred 180-660, y=270
                drawCentred(cs, nameFont,    24, name,     180, 660, 270);
                // Date: Lora Regular 14pt, centred 340-520, y=170
                drawCentred(cs, regularFont, 14, date,     340, 520, 170);
                // Minister: Lora Regular 12pt, centred 140-320, y=133
                if (minister != null && !minister.isEmpty()) {
                    drawCentred(cs, regularFont, 12, minister, 140, 320, 133);
                }
            }
            doc.save(outPath);
        }
    }

    // =========================================================
    // CERTIFICATE OF APPRECIATION
    // Font: Caladea-Bold for the name (authoritative)
    //       Caladea-Regular for date
    // =========================================================
    public static void generateAppreciation(String name, String date,
                                            String outPath) throws Exception {
        File tpl = new File(CACHE, "CertificateOfAppreciation.pdf");
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBufferedFile(tpl))) {
            PDPage page = doc.getPage(0);
            PDType0Font boldFont    = caladeaBold(doc);
            PDType0Font regularFont = caladeaRegular(doc);

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                // Name: Caladea Bold 24pt, centred 80-580, y=270
                drawCentred(cs, boldFont,    24, name, 80, 580, 270);
                // Date: Caladea Regular 14pt, centred 80-220, y=90
                drawCentred(cs, regularFont, 14, date, 80, 220,  90);
            }
            doc.save(outPath);
        }
    }

    // =========================================================
    // MARRIAGE BLESSING CERTIFICATE
    // Font: Lora-Italic for bride and groom names (romantic)
    //       Lora-Regular for date
    // =========================================================
    public static void generateBlessing(String bride, String groom, String date,
                                        String photoPath, String outPath) throws Exception {
        File tpl = new File(CACHE, "BlessingCertificate.pdf");
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBufferedFile(tpl))) {
            PDPage page = doc.getPage(0);
            PDType0Font italicFont  = loraItalic(doc);
            PDType0Font regularFont = loraRegular(doc);

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                // Bride: Lora Italic 20pt, centred 60-400, y=375
                drawCentred(cs, italicFont,  20, bride, 60, 400, 375);
                // Groom: Lora Italic 20pt, centred 60-400, y=315
                drawCentred(cs, italicFont,  20, groom, 60, 400, 315);
                // Date: Lora Regular 13pt, centred 140-320, y=88
                drawCentred(cs, regularFont, 13, date, 140, 320,  88);

                // Photo: x=520 y=180 w=220 h=260 (bottom-left origin)
                if (photoPath != null && !photoPath.isEmpty()) {
                    File img = new File(photoPath);
                    if (img.exists()) {
                        PDImageXObject photo = PDImageXObject.createFromFile(photoPath, doc);
                        cs.drawImage(photo, 520, 180, 220, 260);
                    }
                }
            }
            doc.save(outPath);
        }
    }

    // =========================================================
    // DEATH ANNOUNCEMENT
    //
    // Template: DeathAnnouncement.pdf (A5, 297.75 x 419.25 pts)
    // Layout (PDFBox bottom-left origin):
    //   Name  — Lora Italic 22pt, centred x=135–280, baseline y=305
    //   Date  — Lora Regular 14pt, centred x=105–280, baseline y=172
    //   Time  — Lora Regular 14pt, centred x=105–280, baseline y=158
    //   Venue — Lora Regular 14pt, centred x=105–280, baseline y=144
    //
    // Output: PNG image (not PDF) so it can be shared directly to
    // WhatsApp/social platforms without a PDF viewer.
    // Resolution: 200 DPI → 828 x 1165 px
    // =========================================================
    public static void generateAnnouncement(String deceasedName,
                                            String burialDate,
                                            String burialTime,
                                            String venue,
                                            String outImagePath) throws Exception {
        File tpl = new File(CACHE, "DeathAnnouncement.pdf");

        // Step 1: Write text into a filled PDF (temp file)
        File filledPdf = File.createTempFile("announcement_filled_", ".pdf");
        filledPdf.deleteOnExit();

        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBufferedFile(tpl))) {
            PDPage page = doc.getPage(0);
            PDType0Font nameFont   = loraItalic(doc);   // elegant italic for the name
            PDType0Font detailFont = loraRegular(doc);   // clean regular for details

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                // ── Deceased name (black text — light area) ───────────────
                cs.setNonStrokingColor(0f, 0f, 0f);

                // Lora Italic, centred 135–280, baseline y=305
                // Auto-shrink if name is very long so it stays within the zone
                float namePtSize   = 22f;
                float nameZoneWidth = 280f - 135f;
                float nameWidth    = nameFont.getStringWidth(deceasedName) / 1000f * namePtSize;
                while (nameWidth > nameZoneWidth - 4 && namePtSize > 10f) {
                    namePtSize -= 0.5f;
                    nameWidth  = nameFont.getStringWidth(deceasedName) / 1000f * namePtSize;
                }
                drawCentred(cs, nameFont, namePtSize, deceasedName, 135f, 280f, 305f);

                // ── Funeral details (white text — dark background area) ───
                cs.setNonStrokingColor(1f, 1f, 1f);

                // Date — centred 100–280, baseline y=172
                drawCentred(cs, detailFont, 14f, burialDate, 105f, 280f, 172f);

                // Time — centred 100–280, baseline y=158
                drawCentred(cs, detailFont, 14f, burialTime, 105f, 280f, 158f);

                // Venue — centred 100–280, baseline y=144
                drawCentred(cs, detailFont, 14f, venue,      105f, 280f, 144f);
            }
            doc.save(filledPdf.getAbsolutePath());
        }

        // Step 2: Render the filled PDF page to a high-res PNG
        try (PDDocument doc = Loader.loadPDF(
                new RandomAccessReadBufferedFile(filledPdf))) {
            PDFRenderer renderer = new PDFRenderer(doc);
            // 200 DPI → 828 x 1165 px — clear enough for sharing
            BufferedImage image = renderer.renderImageWithDPI(0, 200);
            ImageIO.write(image, "PNG", new File(outImagePath));
        }
    }
}
