package com.afmvfcc.controllers;

import com.afmvfcc.models.Event;
import com.afmvfcc.models.Member;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import org.apache.poi.util.Units;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.math.BigInteger;

public class DocumentExporter {

    // ── Brand colours ─────────────────────────────────────────
    private static final DeviceRgb NAVY = new DeviceRgb(0x1A, 0x2B, 0x4A);
    private static final DeviceRgb GOLD = new DeviceRgb(0xC9, 0xA8, 0x4C);
    private static final DeviceRgb LIGHT_ROW = new DeviceRgb(0xF5, 0xF7, 0xFA);
    private static final DeviceRgb WHITE = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb HDR_TXT = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb BODY = new DeviceRgb(0x1E, 0x21, 0x30);
    private static final DeviceRgb MUTED = new DeviceRgb(0x90, 0x99, 0xAA);
    private static final DeviceRgb GREEN_BG = new DeviceRgb(0xE6, 0xF4, 0xEC);
    private static final DeviceRgb GREEN_FG = new DeviceRgb(0x1E, 0x5C, 0x33);
    private static final DeviceRgb AMBER_BG = new DeviceRgb(0xFD, 0xF3, 0xE0);
    private static final DeviceRgb AMBER_FG = new DeviceRgb(0x7A, 0x4F, 0x00);
    private static final DeviceRgb RED_BG = new DeviceRgb(0xFD, 0xEC, 0xEA);
    private static final DeviceRgb RED_FG = new DeviceRgb(0x7A, 0x1A, 0x1A);
    private static final DeviceRgb BLUE_BG = new DeviceRgb(0xE6, 0xF0, 0xFA);
    private static final DeviceRgb BLUE_FG = new DeviceRgb(0x1A, 0x3D, 0x6B);

    // POI hex strings
    private static final String POI_NAVY = "1A2B4A", POI_GOLD = "C9A84C";
    private static final String POI_LIGHT = "F5F7FA", POI_WHITE = "FFFFFF";
    private static final String POI_BODY = "1E2130";
    private static final String POI_GRBG = "E6F4EC", POI_GRFG = "1E5C33";
    private static final String POI_AMBG = "FDF3E0", POI_AMFG = "7A4F00";
    private static final String POI_REBG = "FDECEA", POI_REFG = "7A1A1A";
    private static final String POI_BLBG = "E6F0FA", POI_BLFG = "1A3D6B";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ── Logo ─────────────────────────────────────────────────
    private static byte[] loadLogoBytes() {
        // 1. Classpath resource (bundled in the JAR)
        try (InputStream in = DocumentExporter.class
                .getResourceAsStream("/com/afmvfcc/images/afm_logo.png")) {
            if (in != null)
                return in.readAllBytes();
        } catch (Exception ignored) {
        }
        // 2. Cert folder next to the app
        for (String p : new String[] {
                System.getProperty("user.home") + File.separator + ".afmvfcc_certs" + File.separator + "afm_logo.png",
                System.getProperty("user.home") + File.separator + "afm_logo.png" }) {
            File f = new File(p);
            if (f.exists()) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    return fis.readAllBytes();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    // ── Data records ──────────────────────────────────────────
    /** Used by MemberPdfExporter and the inline member-list export. */
    public record MemberRow(String fullName, String phone, String subBranch,
            String ministries, String availability,
            String status, String dateJoined,
            String maritalStatus,      // NEW
            String employmentStatus,   // NEW
            String spouseInfo) {       // NEW
    }

    public record WelfareRow(String memberName, String reason, String assignedAgent,
            String report, String status, String dateOpened) {
    }

    public record AttendanceRow(String memberName, String ministry, String status) {
    }

    // ══════════════════════════════════════════════════════════
    // PUBLIC ENTRY POINTS
    // ══════════════════════════════════════════════════════════

    public static void exportMembers(List<MemberRow> rows) {
        String fmt = pickFormat(true);
        if (fmt == null)
            return;
        File out = chooseSave("AFM_VFCC_Members_" + today(), fmt);
        if (out == null)
            return;
        bg(() -> {
            if ("pdf".equals(fmt))
                membersPdf(rows, out);
            else if ("xlsx".equals(fmt))
                membersExcel(rows, out);
            else
                membersDocx(rows, out);
            showOk("Members list saved to:\n" + out.getAbsolutePath());
        });
    }

    public static void exportWelfare(List<WelfareRow> rows) {
        String fmt = pickFormat(false);
        if (fmt == null)
            return;
        File out = chooseSave("AFM_VFCC_Welfare_" + today(), fmt);
        if (out == null)
            return;
        bg(() -> {
            if ("pdf".equals(fmt))
                welfarePdf(rows, out);
            else
                welfareDocx(rows, out);
            showOk("Welfare report saved to:\n" + out.getAbsolutePath());
        });
    }

    public static void exportAttendance(String sessName, String sessDate,
            List<AttendanceRow> rows) {
        String fmt = pickFormat(true);
        if (fmt == null)
            return;
        File out = chooseSave("AFM_VFCC_Attendance_" + today(), fmt);
        if (out == null)
            return;
        bg(() -> {
            if ("pdf".equals(fmt))
                attendancePdf(sessName, sessDate, rows, out);
            else if ("xlsx".equals(fmt))
                attendanceExcel(sessName, sessDate, rows, out);
            else
                attendanceDocx(sessName, sessDate, rows, out);
            showOk("Attendance saved to:\n" + out.getAbsolutePath());
        });
    }

    public static void exportEvents(List<Event> events, String range, String cat) {
        String fmt = pickFormat(false);
        if (fmt == null)
            return;
        File out = chooseSave("AFM_VFCC_Events_" + today(), fmt);
        if (out == null)
            return;
        bg(() -> {
            if ("pdf".equals(fmt))
                eventsPdf(events, range, cat, out);
            else
                eventsDocx(events, range, cat, out);
            showOk("Events saved to:\n" + out.getAbsolutePath());
        });
    }

    public static void exportMemberForm(Member member) {
        if (member == null || member.getId() <= 0) {
            showErr("Cannot print: member has not been saved to the database yet.\n" +
                    "Please save first, then use Print Form.");
            return;
        }
        String safe = member.getFullName() != null
                ? member.getFullName().replaceAll("[^\\w\\s-]", "").replace(' ', '_')
                : "Member";
        File out = chooseSave("AFM_VFCC_Member_" + safe + "_" + today(), "pdf");
        if (out == null)
            return;
        bg(() -> {
            memberFormPdf(member, out);
            showOk("Member form saved to:\n" + out.getAbsolutePath());
        });
    }

    // ── Documentation (User Guide + Policy) ─────────────────────
    record DocSection(String heading, List<String> body) {}

    public static void exportUserGuide() {
        String fmt = pickFormat(false);
        if (fmt == null) return;
        File out = chooseSave("AFM_VFCC_User_Guide_" + today(), fmt);
        if (out == null) return;
        List<DocSection> sections = UserGuideContent.sections().stream()
                .map(s -> new DocSection(s.heading(), s.body())).toList();
        bg(() -> {
            if ("pdf".equals(fmt)) userGuidePdf(sections, out);
            else userGuideDocx(sections, out);
            showOk("User Guide saved to:\n" + out.getAbsolutePath());
        });
    }

    public static void exportPolicy() {
        File out = chooseSave("AFM_VFCC_System_Usage_Policy_" + today(), "pdf");
        if (out == null) return;
        List<DocSection> sections = PolicyContent.sections().stream()
                .map(s -> new DocSection(s.heading(), s.body())).toList();
        bg(() -> {
            policyPdf(sections, out);
            showOk("Policy document saved to:\n" + out.getAbsolutePath());
        });
    }

    // ══════════════════════════════════════════════════════════
    // PDF — iText7
    // ══════════════════════════════════════════════════════════

    private static void membersPdf(List<MemberRow> rows, File out) throws Exception {
        Document doc = openDoc(out, true);
        PdfFont b = bold(), r = reg();
        letterhead(doc, b, r, "Member List");

        float[] cw = { 150f, 80f, 90f, 120f, 80f, 60f, 85f };
        Table t = table(cw);
        for (String h : new String[] { "Full Name", "Phone", "Sub-Branch", "Ministries", "Availability", "Status",
                "Date Joined" })
            t.addHeaderCell(hdrCell(h, b));
        for (int i = 0; i < rows.size(); i++) {
            MemberRow m = rows.get(i);
            DeviceRgb bg = i % 2 == 0 ? WHITE : LIGHT_ROW;
            t.addCell(dc(m.fullName(), bg, BODY, r, false));
            t.addCell(dc(m.phone(), bg, BODY, r, false));
            t.addCell(dc(m.subBranch(), bg, BODY, r, false));
            t.addCell(dc(m.ministries(), bg, BODY, r, false));
            t.addCell(dc(m.availability(), bg, BODY, r, false));
            DeviceRgb[] sc = memSt(m.status());
            t.addCell(dc(m.status(), sc[0], sc[1], b, true));
            t.addCell(dc(m.dateJoined(), bg, BODY, r, false));
        }
        if (rows.isEmpty())
            pad(t, 7);
        doc.add(t);
        footer(doc, r, "Total members: " + rows.size());
        doc.close();
    }

    private static void welfarePdf(List<WelfareRow> rows, File out) throws Exception {
        Document doc = openDoc(out, true);
        PdfFont b = bold(), r = reg();
        letterhead(doc, b, r, "Welfare Cases Report");

        float[] cw = { 110f, 125f, 105f, 155f, 75f, 90f };
        Table t = table(cw);
        for (String h : new String[] { "Member", "Reason", "Agent", "Report", "Status", "Date Opened" })
            t.addHeaderCell(hdrCell(h, b));
        for (int i = 0; i < rows.size(); i++) {
            WelfareRow w = rows.get(i);
            DeviceRgb bg = i % 2 == 0 ? WHITE : LIGHT_ROW;
            t.addCell(dc(w.memberName(), bg, BODY, r, false));
            t.addCell(dc(w.reason(), bg, BODY, r, false));
            t.addCell(dc(w.assignedAgent(), bg, BODY, r, false));
            t.addCell(dc(nvl(w.report()), bg, BODY, r, false));
            DeviceRgb[] sc = wfSt(w.status());
            t.addCell(dc(w.status(), sc[0], sc[1], b, true));
            t.addCell(dc(w.dateOpened(), bg, BODY, r, false));
        }
        if (rows.isEmpty())
            pad(t, 6);
        doc.add(t);
        footer(doc, r, "Total cases: " + rows.size());
        doc.close();
    }

    private static void attendancePdf(String sn, String sd,
            List<AttendanceRow> rows, File out) throws Exception {
        Document doc = openDoc(out, false);
        PdfFont b = bold(), r = reg();
        letterhead(doc, b, r, "Attendance Register");
        doc.add(new Paragraph(sn + "  \u2014  " + sd)
                .setFont(r).setFontSize(11).setFontColor(MUTED).setMarginBottom(12));

        float[] cw = { 230f, 165f, 75f, 90f };
        Table t = table(cw);
        for (String h : new String[] { "Member Name", "Ministry", "Status", "Date" })
            t.addHeaderCell(hdrCell(h, b));
        long present = rows.stream().filter(a -> "Present".equals(a.status())).count();
        for (int i = 0; i < rows.size(); i++) {
            AttendanceRow a = rows.get(i);
            DeviceRgb bg = i % 2 == 0 ? WHITE : LIGHT_ROW;
            boolean p = "Present".equals(a.status());
            t.addCell(dc(a.memberName(), bg, BODY, r, false));
            t.addCell(dc(a.ministry(), bg, BODY, r, false));
            t.addCell(dc(a.status(), p ? GREEN_BG : RED_BG, p ? GREEN_FG : RED_FG, b, true));
            t.addCell(dc(sd, bg, BODY, r, false));
        }
        if (rows.isEmpty())
            pad(t, 4);
        doc.add(t);
        footer(doc, r, "Present: " + present + "   |   Total: " + rows.size());
        doc.close();
    }

    private static void eventsPdf(List<Event> events, String range, String cat, File out) throws Exception {
        Document doc = openDoc(out, false);
        PdfFont b = bold(), r = reg();
        letterhead(doc, b, r, "Church Events");
        doc.add(new Paragraph("Period: " + range + ("All".equals(cat) ? "" : "   |   Category: " + cat))
                .setFont(r).setFontSize(11).setFontColor(MUTED).setMarginBottom(12));

        float[] cw = { 130f, 75f, 50f, 100f, 70f, 130f };
        Table t = table(cw);
        for (String h : new String[] { "Title", "Date", "Time", "Location", "Category", "Description" })
            t.addHeaderCell(hdrCell(h, b));
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");
        for (int i = 0; i < events.size(); i++) {
            Event ev = events.get(i);
            DeviceRgb bg = i % 2 == 0 ? WHITE : LIGHT_ROW;
            t.addCell(dc(nvl(ev.getTitle()), bg, BODY, b, false));
            t.addCell(dc(ev.getEventDate() != null ? ev.getEventDate().format(FMT) : "—", bg, BODY, r, false));
            t.addCell(dc(ev.getEventTime() != null ? ev.getEventTime().format(tf) : "—", bg, BODY, r, true));
            t.addCell(dc(nvl(ev.getLocation()), bg, BODY, r, false));
            DeviceRgb[] cc = evCat(ev.getCategory());
            t.addCell(dc(nvl(ev.getCategory()), cc[0], cc[1], b, true));
            t.addCell(dc(nvl(ev.getDescription()), bg, MUTED, r, false));
        }
        if (events.isEmpty())
            pad(t, 6);
        doc.add(t);
        footer(doc, r, "Total events: " + events.size());
        doc.close();
    }

    private static void memberFormPdf(Member m, File out) throws Exception {
        Document doc = openDoc(out, false);
        PdfFont b = bold(), r = reg();
        letterhead(doc, b, r, "Member Profile");

        doc.add(new Paragraph(nvl(m.getFullName()))
                .setFont(b).setFontSize(18).setFontColor(NAVY).setMarginBottom(4));
        DeviceRgb[] sc = memSt(m.getStatusLabel());
        doc.add(new Paragraph(m.getStatusLabel() + "  |  " + m.getAvailabilityLabel())
                .setFont(r).setFontSize(11).setFontColor(sc[1]).setMarginBottom(14));

        float[] cw = { 130f, 370f };
        Table t = new Table(UnitValue.createPointArray(cw));
        t.setWidth(UnitValue.createPercentValue(100)).setFixedLayout();

        profileRow(t, b, r, "Phone", m.getPhone());
        profileRow(t, b, r, "Email", m.getEmail());
        profileRow(t, b, r, "Gender", m.getGender());
        profileRow(t, b, r, "Date of Birth", m.getDateOfBirth() != null ? m.getDateOfBirth().format(FMT) : null);
        profileRow(t, b, r, "Marital Status", m.getMaritalStatus());
        profileRow(t, b, r, "Employment Status", m.getEmploymentStatus());

        String spouseText = "N/A";
        if ("Married".equals(m.getMaritalStatus())) {
            spouseText = m.isSpouseMember() ? "Yes" : "No";
        }
        profileRow(t, b, r, "Spouse in Church", spouseText);

        profileRow(t, b, r, "Address", m.getAddress());
        profileRow(t, b, r, "Sub-Branch", m.getSubBranchName());
        profileRow(t, b, r, "Family", m.getFamilyName());
        profileRow(t, b, r, "Date Joined", m.getDateJoined() != null ? m.getDateJoined().format(FMT) : null);
        profileRow(t, b, r, "Baptism Date", m.getBaptismDate() != null ? m.getBaptismDate().format(FMT) : null);
        profileRow(t, b, r, "Next of Kin", m.getNextOfKinName());
        profileRow(t, b, r, "NOK Phone", m.getNextOfKinPhone());

        doc.add(t);
        footer(doc, r, null);
        doc.close();
    }

    private static void userGuidePdf(List<DocSection> sections, File out) throws Exception {
        Document doc = openDoc(out, false);
        PdfFont b = bold(), r = reg();
        letterhead(doc, b, r, "User Guide");
        doc.add(new Paragraph("A complete guide to every module in the AFM VFCC Church Management System.")
                .setFont(r).setFontSize(10).setFontColor(MUTED).setMarginBottom(16));
        for (DocSection s : sections) {
            doc.add(new Paragraph(s.heading())
                    .setFont(b).setFontSize(13).setFontColor(NAVY)
                    .setMarginTop(14).setMarginBottom(6));
            for (String line : s.body()) {
                boolean bullet = line.startsWith("- ");
                Paragraph p = new Paragraph(bullet ? "•  " + line.substring(2) : line)
                        .setFont(r).setFontSize(10.5f).setFontColor(BODY)
                        .setMarginBottom(bullet ? 3 : 8);
                if (bullet) p.setMarginLeft(14);
                doc.add(p);
            }
        }
        footer(doc, r, null);
        doc.close();
    }

    private static void policyPdf(List<DocSection> sections, File out) throws Exception {
        Document doc = openDoc(out, false);
        PdfFont b = bold(), r = reg();
        DeviceRgb black = new DeviceRgb(0, 0, 0);

        doc.add(new Paragraph("AFM VFCC CHURCH MANAGEMENT SYSTEM")
                .setFont(b).setFontSize(14).setFontColor(black).setMarginBottom(0));
        doc.add(new Paragraph("SYSTEM USAGE POLICY")
                .setFont(b).setFontSize(18).setFontColor(black).setMarginBottom(4));
        doc.add(new Paragraph()
                .setBorderBottom(new SolidBorder(black, 1f))
                .setMarginTop(4).setMarginBottom(14));

        for (DocSection s : sections) {
            doc.add(new Paragraph(s.heading())
                    .setFont(b).setFontSize(12).setFontColor(black)
                    .setMarginTop(12).setMarginBottom(6));
            for (String line : s.body()) {
                boolean bullet = line.startsWith("- ");
                Paragraph p = new Paragraph(bullet ? "•  " + line.substring(2) : line)
                        .setFont(r).setFontSize(10.5f).setFontColor(black)
                        .setMarginBottom(bullet ? 3 : 8);
                if (bullet) p.setMarginLeft(14);
                doc.add(p);
            }
        }
        doc.add(new Paragraph("Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")))
                .setFont(r).setFontSize(8).setFontColor(black)
                .setTextAlignment(TextAlignment.RIGHT).setMarginTop(16));
        doc.close();
    }

    // ── PDF helpers ───────────────────────────────────────────

    private static Document openDoc(File out, boolean landscape) throws Exception {
        PdfDocument pdf = new PdfDocument(new PdfWriter(out));
        PageSize ps = landscape ? PageSize.A4.rotate() : PageSize.A4;
        Document doc = new Document(pdf, ps);
        doc.setMargins(36, 36, 36, 36);
        return doc;
    }

    private static void letterhead(Document doc, PdfFont b, PdfFont r, String title) throws Exception {
        byte[] logo = loadLogoBytes();
        float[] hw = logo != null ? new float[] { 56f, 464f } : new float[] { 520f };
        Table hdr = new Table(UnitValue.createPointArray(hw));
        hdr.setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        if (logo != null) {
            Image img = new Image(ImageDataFactory.create(logo));
            img.scaleToFit(50, 50);
            hdr.addCell(new Cell().add(img)
                    .setBorder(Border.NO_BORDER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(0));
        }

        hdr.addCell(new Cell()
                .setBorder(Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPaddingLeft(logo != null ? 8 : 0)
                .add(new Paragraph("APOSTOLIC FAITH MISSION")
                        .setFont(b).setFontSize(14).setFontColor(NAVY).setMargin(0))
                .add(new Paragraph("VICTORY FELLOWSHIP CHRISTIAN CENTER")
                        .setFont(b).setFontSize(12).setFontColor(NAVY).setMargin(0))
                .add(new Paragraph("Administration Office")
                        .setFont(b).setFontSize(10).setFontColor(GOLD).setMargin(0)));

        doc.add(hdr);
        doc.add(new Paragraph()
                .setBorderBottom(new SolidBorder(GOLD, 1.5f))
                .setMarginTop(4).setMarginBottom(8));
        doc.add(new Paragraph(title)
                .setFont(b).setFontSize(16).setFontColor(NAVY).setMarginBottom(12));
    }

    private static void footer(Document doc, PdfFont r, String extra) {
        String txt = "Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        if (extra != null && !extra.isEmpty())
            txt += "   |   " + extra;
        doc.add(new Paragraph(txt)
                .setFont(r).setFontSize(8).setFontColor(MUTED)
                .setTextAlignment(TextAlignment.RIGHT).setMarginTop(12));
    }

    private static Table table(float[] cw) {
        return new Table(UnitValue.createPointArray(cw))
                .setWidth(UnitValue.createPercentValue(100))
                .setFixedLayout();
    }

    private static Cell hdrCell(String txt, PdfFont b) {
        return new Cell()
                .add(new Paragraph(txt).setFont(b).setFontSize(9).setFontColor(HDR_TXT))
                .setBackgroundColor(NAVY).setBorder(Border.NO_BORDER)
                .setPadding(6).setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static Cell dc(String txt, DeviceRgb bg, DeviceRgb fg, PdfFont f, boolean cen) {
        Paragraph p = new Paragraph(txt != null ? txt : "\u2014")
                .setFont(f != null ? f : reg2()).setFontSize(9).setFontColor(fg);
        if (cen)
            p.setTextAlignment(TextAlignment.CENTER);
        return new Cell().add(p).setBackgroundColor(bg)
                .setBorderTop(new SolidBorder(LIGHT_ROW, 0.5f))
                .setBorderBottom(new SolidBorder(LIGHT_ROW, 0.5f))
                .setBorderLeft(Border.NO_BORDER).setBorderRight(Border.NO_BORDER)
                .setPaddingTop(5).setPaddingBottom(5).setPaddingLeft(6).setPaddingRight(6)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
    }

    private static void profileRow(Table t, PdfFont b, PdfFont r, String lbl, String val) {
        t.addCell(new Cell()
                .add(new Paragraph(lbl).setFont(b).setFontSize(9).setFontColor(MUTED))
                .setBackgroundColor(LIGHT_ROW).setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(WHITE, 2))
                .setPadding(6).setVerticalAlignment(VerticalAlignment.MIDDLE));
        t.addCell(new Cell()
                .add(new Paragraph(val != null && !val.isEmpty() ? val : "\u2014")
                        .setFont(r).setFontSize(10).setFontColor(BODY))
                .setBackgroundColor(WHITE).setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(LIGHT_ROW, 1))
                .setPadding(6).setVerticalAlignment(VerticalAlignment.MIDDLE));
    }

    private static void pad(Table t, int cols) {
        for (int i = 0; i < cols; i++)
            t.addCell(dc("—", WHITE, MUTED, null, true));
    }

    private static PdfFont bold() {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static PdfFont reg() {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static PdfFont reg2() {
        return reg();
    }

    private static DeviceRgb[] memSt(String s) {
        return "Active".equals(s) ? new DeviceRgb[] { GREEN_BG, GREEN_FG } : new DeviceRgb[] { LIGHT_ROW, MUTED };
    }

    private static DeviceRgb[] wfSt(String s) {
        return switch (s != null ? s : "") {
            case "Completed" -> new DeviceRgb[] { GREEN_BG, GREEN_FG };
            case "In Progress" -> new DeviceRgb[] { BLUE_BG, BLUE_FG };
            default -> new DeviceRgb[] { AMBER_BG, AMBER_FG };
        };
    }

    private static DeviceRgb[] evCat(String c) {
        return switch (c != null ? c : "") {
            case "Service" -> new DeviceRgb[] { BLUE_BG, BLUE_FG };
            case "Meeting" -> new DeviceRgb[] { RED_BG, RED_FG };
            case "Outreach" -> new DeviceRgb[] { GREEN_BG, GREEN_FG };
            case "Youth" -> new DeviceRgb[] { new DeviceRgb(0xF3, 0xE8, 0xFF), new DeviceRgb(0x5B, 0x21, 0xB6) };
            default -> new DeviceRgb[] { AMBER_BG, AMBER_FG };
        };
    }

    // ══════════════════════════════════════════════════════════
    // WORD — Apache POI
    // ══════════════════════════════════════════════════════════

    private static void membersDocx(List<MemberRow> rows, File out) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            setLandscape(doc);
            docxLetterhead(doc, "Member List");
            String[] hs = { "Full Name", "Phone", "Sub-Branch", "Ministries", "Availability", "Status", "Date Joined" };
            int[] ws = { 2200, 1300, 1400, 1700, 1200, 1100, 1400 };
            XWPFTable t = doc.createTable(rows.size() + 1, hs.length);
            rmBorders(t);
            for (int c = 0; c < hs.length; c++)
                dxHdr(t.getRow(0).getCell(c), hs[c], ws[c]);
            for (int i = 0; i < rows.size(); i++) {
                MemberRow m = rows.get(i);
                String sh = i % 2 == 0 ? POI_WHITE : POI_LIGHT;
                String[] v = { m.fullName(), m.phone(), m.subBranch(), m.ministries(), m.availability(), m.status(),
                        m.dateJoined() };
                for (int c = 0; c < v.length; c++) {
                    String bg = c == 5 ? stBg(m.status()) : sh;
                    String fg = c == 5 ? stFg(m.status()) : POI_BODY;
                    dxDat(t.getRow(i + 1).getCell(c), v[c], ws[c], bg, fg);
                }
            }
            docxFooter(doc, "Total members: " + rows.size());
            try (FileOutputStream f = new FileOutputStream(out)) {
                doc.write(f);
            }
        }
    }

    private static void welfareDocx(List<WelfareRow> rows, File out) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            setLandscape(doc);
            docxLetterhead(doc, "Welfare Cases Report");
            String[] hs = { "Member", "Reason", "Agent", "Report", "Status", "Date Opened" };
            int[] ws = { 1700, 1900, 1500, 2100, 1100, 1300 };
            XWPFTable t = doc.createTable(rows.size() + 1, hs.length);
            rmBorders(t);
            for (int c = 0; c < hs.length; c++)
                dxHdr(t.getRow(0).getCell(c), hs[c], ws[c]);
            for (int i = 0; i < rows.size(); i++) {
                WelfareRow w = rows.get(i);
                String sh = i % 2 == 0 ? POI_WHITE : POI_LIGHT;
                String[] bgs = { sh, sh, sh, sh, wfBg(w.status()), sh };
                String[] fgs = { POI_BODY, POI_BODY, POI_BODY, POI_BODY, wfFg(w.status()), POI_BODY };
                String[] v = { w.memberName(), w.reason(), w.assignedAgent(), nvl(w.report()), w.status(),
                        w.dateOpened() };
                for (int c = 0; c < v.length; c++)
                    dxDat(t.getRow(i + 1).getCell(c), v[c], ws[c], bgs[c], fgs[c]);
            }
            docxFooter(doc, "Total cases: " + rows.size());
            try (FileOutputStream f = new FileOutputStream(out)) {
                doc.write(f);
            }
        }
    }

    private static void attendanceDocx(String sn, String sd, List<AttendanceRow> rows, File out) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            docxLetterhead(doc, "Attendance Register");
            XWPFRun sr = doc.createParagraph().createRun();
            sr.setText(sn + " \u2014 " + sd);
            sr.setColor("5A6275");
            sr.setFontSize(11);
            sr.addBreak();
            String[] hs = { "Member Name", "Ministry", "Status", "Date" };
            int[] ws = { 3600, 2400, 1500, 1526 };
            long p = rows.stream().filter(a -> "Present".equals(a.status())).count();
            XWPFTable t = doc.createTable(rows.size() + 1, hs.length);
            rmBorders(t);
            for (int c = 0; c < hs.length; c++)
                dxHdr(t.getRow(0).getCell(c), hs[c], ws[c]);
            for (int i = 0; i < rows.size(); i++) {
                AttendanceRow a = rows.get(i);
                String sh = i % 2 == 0 ? POI_WHITE : POI_LIGHT;
                boolean ip = "Present".equals(a.status());
                dxDat(t.getRow(i + 1).getCell(0), a.memberName(), ws[0], sh, POI_BODY);
                dxDat(t.getRow(i + 1).getCell(1), a.ministry(), ws[1], sh, POI_BODY);
                dxDat(t.getRow(i + 1).getCell(2), a.status(), ws[2], ip ? POI_GRBG : POI_REBG,
                        ip ? POI_GRFG : POI_REFG);
                dxDat(t.getRow(i + 1).getCell(3), sd, ws[3], sh, POI_BODY);
            }
            docxFooter(doc, "Present: " + p + "   |   Total: " + rows.size());
            try (FileOutputStream f = new FileOutputStream(out)) {
                doc.write(f);
            }
        }
    }

    private static void eventsDocx(List<Event> events, String range, String cat, File out) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            docxLetterhead(doc, "Church Events");
            XWPFRun sr = doc.createParagraph().createRun();
            sr.setText("Period: " + range + ("All".equals(cat) ? "" : " | Category: " + cat));
            sr.setColor("5A6275");
            sr.setFontSize(11);
            sr.addBreak();
            String[] hs = { "Title", "Date", "Time", "Location", "Category", "Description" };
            int[] ws = { 1900, 1300, 900, 1500, 1100, 2000 };
            XWPFTable t = doc.createTable(events.size() + 1, hs.length);
            rmBorders(t);
            for (int c = 0; c < hs.length; c++)
                dxHdr(t.getRow(0).getCell(c), hs[c], ws[c]);
            DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");
            for (int i = 0; i < events.size(); i++) {
                Event ev = events.get(i);
                String sh = i % 2 == 0 ? POI_WHITE : POI_LIGHT;
                dxDat(t.getRow(i + 1).getCell(0), nvl(ev.getTitle()), ws[0], sh, POI_BODY);
                dxDat(t.getRow(i + 1).getCell(1), ev.getEventDate() != null ? ev.getEventDate().format(FMT) : "—",
                        ws[1], sh, POI_BODY);
                dxDat(t.getRow(i + 1).getCell(2), ev.getEventTime() != null ? ev.getEventTime().format(tf) : "—", ws[2],
                        sh, POI_BODY);
                dxDat(t.getRow(i + 1).getCell(3), nvl(ev.getLocation()), ws[3], sh, POI_BODY);
                dxDat(t.getRow(i + 1).getCell(4), nvl(ev.getCategory()), ws[4], evCatBg(ev.getCategory()),
                        evCatFg(ev.getCategory()));
                dxDat(t.getRow(i + 1).getCell(5), nvl(ev.getDescription()), ws[5], sh, "9099AA");
            }
            docxFooter(doc, "Total events: " + events.size());
            try (FileOutputStream f = new FileOutputStream(out)) {
                doc.write(f);
            }
        }
    }

    private static void userGuideDocx(List<DocSection> sections, File out) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            docxLetterhead(doc, "User Guide");
            XWPFRun sub = doc.createParagraph().createRun();
            sub.setText("A complete guide to every module in the AFM VFCC Church Management System.");
            sub.setColor("9099AA");
            sub.setFontSize(10);
            sub.setItalic(true);

            for (DocSection s : sections) {
                XWPFParagraph hp = doc.createParagraph();
                hp.setSpacingBefore(280);
                hp.setSpacingAfter(120);
                dxRun(hp, s.heading(), POI_NAVY, 14, true);

                for (String line : s.body()) {
                    boolean bullet = line.startsWith("- ");
                    XWPFParagraph p = doc.createParagraph();
                    p.setSpacingAfter(bullet ? 60 : 160);
                    if (bullet) p.setIndentationLeft(280);
                    dxRun(p, bullet ? "•  " + line.substring(2) : line, POI_BODY, 11, false);
                }
            }
            docxFooter(doc, null);
            try (FileOutputStream f = new FileOutputStream(out)) {
                doc.write(f);
            }
        }
    }

    // ── Word letterhead ───────────────────────────────────────
    private static void docxLetterhead(XWPFDocument doc, String title) throws Exception {
        byte[] logo = loadLogoBytes();
        XWPFTable hdr = doc.createTable(1, logo != null ? 2 : 1);
        rmBorders(hdr);
        setTblW(hdr, 9026);

        if (logo != null) {
            XWPFTableCell lc = hdr.getRow(0).getCell(0);
            setCW(lc, 1000);
            try (InputStream ls = new ByteArrayInputStream(logo)) {
                lc.getParagraphs().get(0).createRun()
                        .addPicture(ls, XWPFDocument.PICTURE_TYPE_PNG, "logo.png",
                                Units.toEMU(50), Units.toEMU(50));
            }
        }

        XWPFTableCell tc = hdr.getRow(0).getCell(logo != null ? 1 : 0);
        setCW(tc, logo != null ? 8026 : 9026);
        dxRun(tc.getParagraphs().get(0), "APOSTOLIC FAITH MISSION", POI_NAVY, 16, true);
        dxRun(tc.addParagraph(), "VICTORY FELLOWSHIP CHRISTIAN CENTER", POI_NAVY, 14, true);
        dxRun(tc.addParagraph(), "Administration Office", POI_GOLD, 12, true);

        // Gold rule
        XWPFParagraph rule = doc.createParagraph();
        rule.getCTP().addNewPPr();
        CTPBdr pBdr = rule.getCTP().getPPr().addNewPBdr();
        CTBorder bot = pBdr.addNewBottom();
        bot.setVal(STBorder.SINGLE);
        bot.setSz(BigInteger.valueOf(12));
        bot.setColor(POI_GOLD);
        bot.setSpace(BigInteger.valueOf(4));

        XWPFParagraph tp = doc.createParagraph();
        tp.setSpacingBefore(160);
        tp.setSpacingAfter(200);
        dxRun(tp, title, POI_NAVY, 18, true);
    }

    private static void docxFooter(XWPFDocument doc, String extra) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.RIGHT);
        p.setSpacingBefore(200);
        String txt = "Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy"));
        if (extra != null && !extra.isEmpty())
            txt += "   |   " + extra;
        dxRun(p, txt, "9099AA", 8, false);
    }

    private static void dxHdr(XWPFTableCell c, String txt, int w) {
        setCW(c, w);
        setShd(c, POI_NAVY);
        XWPFRun r = c.getParagraphs().get(0).createRun();
        r.setText(txt);
        r.setBold(true);
        r.setColor(POI_WHITE);
        r.setFontSize(9);
        setCellMargins(c);
    }

    private static void dxDat(XWPFTableCell c, String txt, int w, String bg, String fg) {
        setCW(c, w);
        setShd(c, bg);
        XWPFRun r = c.getParagraphs().get(0).createRun();
        r.setText(txt != null ? txt : "\u2014");
        r.setColor(fg);
        r.setFontSize(9);
        setCellMargins(c);
    }

    private static void dxRun(XWPFParagraph p, String txt, String col, int sz, boolean bold) {
        XWPFRun r = p.createRun();
        r.setText(txt);
        r.setColor(col);
        r.setFontSize(sz);
        r.setBold(bold);
    }

    private static void setShd(XWPFTableCell c, String hex) {
        CTTcPr pr = orTcPr(c);
        CTShd s = pr.isSetShd() ? pr.getShd() : pr.addNewShd();
        s.setVal(STShd.CLEAR);
        s.setColor("auto");
        s.setFill(hex);
    }

    private static void setCW(XWPFTableCell c, int dxa) {
        CTTcPr pr = orTcPr(c);
        CTTblWidth w = pr.isSetTcW() ? pr.getTcW() : pr.addNewTcW();
        w.setW(BigInteger.valueOf(dxa));
        w.setType(STTblWidth.DXA);
    }

    private static void setCellMargins(XWPFTableCell c) {
        CTTcPr pr = orTcPr(c);
        CTTcMar m = pr.isSetTcMar() ? pr.getTcMar() : pr.addNewTcMar();
        BigInteger p = BigInteger.valueOf(60), lr = BigInteger.valueOf(120);
        setW(m.isSetTop() ? m.getTop() : m.addNewTop(), p);
        setW(m.isSetBottom() ? m.getBottom() : m.addNewBottom(), p);
        setW(m.isSetLeft() ? m.getLeft() : m.addNewLeft(), lr);
        setW(m.isSetRight() ? m.getRight() : m.addNewRight(), lr);
    }

    private static void setW(CTTblWidth w, BigInteger v) {
        w.setW(v);
        w.setType(STTblWidth.DXA);
    }

    private static CTTcPr orTcPr(XWPFTableCell c) {
        CTTc tc = c.getCTTc();
        return tc.isSetTcPr() ? tc.getTcPr() : tc.addNewTcPr();
    }

    private static void rmBorders(XWPFTable t) {
        CTTblPr p = t.getCTTbl().getTblPr();
        if (p == null)
            p = t.getCTTbl().addNewTblPr();
        CTTblBorders b = p.isSetTblBorders() ? p.getTblBorders() : p.addNewTblBorders();
        for (CTBorder bd : new CTBorder[] {
                b.isSetTop() ? b.getTop() : b.addNewTop(), b.isSetBottom() ? b.getBottom() : b.addNewBottom(),
                b.isSetLeft() ? b.getLeft() : b.addNewLeft(), b.isSetRight() ? b.getRight() : b.addNewRight(),
                b.isSetInsideH() ? b.getInsideH() : b.addNewInsideH(),
                b.isSetInsideV() ? b.getInsideV() : b.addNewInsideV()
        }) {
            bd.setVal(STBorder.NONE);
            bd.setSz(BigInteger.ZERO);
            bd.setSpace(BigInteger.ZERO);
            bd.setColor("auto");
        }
    }

    private static void setTblW(XWPFTable t, int dxa) {
        CTTblPr p = t.getCTTbl().getTblPr();
        if (p == null)
            p = t.getCTTbl().addNewTblPr();
        CTTblWidth w = p.isSetTblW() ? p.getTblW() : p.addNewTblW();
        w.setW(BigInteger.valueOf(dxa));
        w.setType(STTblWidth.DXA);
    }

    private static void setLandscape(XWPFDocument doc) {
        CTBody body = doc.getDocument().getBody();
        CTSectPr s = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageSz ps = s.isSetPgSz() ? s.getPgSz() : s.addNewPgSz();
        ps.setW(BigInteger.valueOf(16838));
        ps.setH(BigInteger.valueOf(11906));
        ps.setOrient(STPageOrientation.LANDSCAPE);
    }

    // POI colour
    private static String stBg(String s) {
        return "Active".equals(s) ? POI_GRBG : POI_LIGHT;
    }

    private static String stFg(String s) {
        return "Active".equals(s) ? POI_GRFG : "9099AA";
    }

    private static String wfBg(String s) {
        return switch (s != null ? s : "") {
            case "Completed" -> POI_GRBG;
            case "In Progress" -> POI_BLBG;
            default -> POI_AMBG;
        };
    }

    private static String wfFg(String s) {
        return switch (s != null ? s : "") {
            case "Completed" -> POI_GRFG;
            case "In Progress" -> POI_BLFG;
            default -> POI_AMFG;
        };
    }

    private static String evCatBg(String c) {
        return switch (c != null ? c : "") {
            case "Service" -> POI_BLBG;
            case "Meeting" -> POI_REBG;
            case "Outreach" -> POI_GRBG;
            case "Youth" -> "F3E8FF";
            default -> POI_AMBG;
        };
    }

    private static String evCatFg(String c) {
        return switch (c != null ? c : "") {
            case "Service" -> POI_BLFG;
            case "Meeting" -> POI_REFG;
            case "Outreach" -> POI_GRFG;
            case "Youth" -> "5B21B6";
            default -> POI_AMFG;
        };
    }

    // ══════════════════════════════════════════════════════════
    // EXCEL — Apache POI
    // ══════════════════════════════════════════════════════════

    private static void membersExcel(List<MemberRow> rows, File out) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Members");

            XSSFCellStyle titleSt = xStyle(wb, "1A2B4A", true, (short) 14, "FFFFFF");
            XSSFCellStyle hdrSt = xStyle(wb, "1A2B4A", true, (short) 10, "FFFFFF");
            XSSFCellStyle actSt = xStyle(wb, POI_GRBG, false, (short) 9, POI_GRFG);
            XSSFCellStyle inaSt = xStyle(wb, POI_LIGHT, false, (short) 9, "9099AA");
            XSSFCellStyle evenSt = xStyle(wb, "FFFFFF", false, (short) 9, POI_BODY);
            XSSFCellStyle oddSt = xStyle(wb, POI_LIGHT, false, (short) 9, POI_BODY);

            xCell(sh.createRow(0), 0, "AFM VFCC — Member List", titleSt);
            xCell(sh.createRow(1), 0,
                    "Generated: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")), inaSt);

            Row hdr = sh.createRow(3);
            String[] hs = { "#", "Full Name", "Phone", "Sub-Branch", "Ministries", "Availability", "Status",
                    "Date Joined" };
            for (int i = 0; i < hs.length; i++) {
                xCell(hdr, i, hs[i], hdrSt);
            }

            for (int i = 0; i < rows.size(); i++) {
                MemberRow m = rows.get(i);
                Row row = sh.createRow(4 + i);
                XSSFCellStyle rs = (i % 2 == 0) ? evenSt : oddSt;

                xCell(row, 0, String.valueOf(i + 1), rs);
                xCell(row, 1, nvl(m.fullName()), rs);
                xCell(row, 2, nvl(m.phone()), rs);
                xCell(row, 3, nvl(m.subBranch()), rs);
                xCell(row, 4, nvl(m.ministries()), rs);
                xCell(row, 5, nvl(m.availability()), rs);
                xCell(row, 6, nvl(m.status()), "Active".equals(m.status()) ? actSt : inaSt);
                xCell(row, 7, nvl(m.dateJoined()), rs);
            }

            xCell(sh.createRow(4 + rows.size() + 1), 0, "Total members: " + rows.size(), inaSt);

            for (int i = 0; i < hs.length; i++)
                sh.autoSizeColumn(i);

            try (FileOutputStream f = new FileOutputStream(out)) {
                wb.write(f);
            }
        }
    }

    private static void attendanceExcel(String sn, String sd, List<AttendanceRow> rows, File out) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sh = wb.createSheet("Attendance");

            XSSFCellStyle titleSt = xStyle(wb, "1A2B4A", true, (short) 14, "FFFFFF");
            XSSFCellStyle hdrSt = xStyle(wb, "1A2B4A", true, (short) 10, "FFFFFF");
            XSSFCellStyle preSt = xStyle(wb, POI_GRBG, false, (short) 9, POI_GRFG);
            XSSFCellStyle absSt = xStyle(wb, POI_REBG, false, (short) 9, POI_REFG);
            XSSFCellStyle evenSt = xStyle(wb, "FFFFFF", false, (short) 9, POI_BODY);
            XSSFCellStyle oddSt = xStyle(wb, POI_LIGHT, false, (short) 9, POI_BODY);

            xCell(sh.createRow(0), 0, "AFM VFCC — Attendance Register", titleSt);
            xCell(sh.createRow(1), 0, sn + " — " + sd, evenSt);

            Row hdr = sh.createRow(3);
            String[] hs = { "#", "Member Name", "Ministry", "Status" };
            for (int i = 0; i < hs.length; i++) {
                xCell(hdr, i, hs[i], hdrSt);
            }

            long p = rows.stream().filter(a -> "Present".equals(a.status())).count();

            for (int i = 0; i < rows.size(); i++) {
                AttendanceRow a = rows.get(i);
                Row row = sh.createRow(4 + i);
                boolean isPresent = "Present".equals(a.status());
                XSSFCellStyle rs = (i % 2 == 0) ? evenSt : oddSt;

                xCell(row, 0, String.valueOf(i + 1), rs);
                xCell(row, 1, nvl(a.memberName()), rs);
                xCell(row, 2, nvl(a.ministry()), rs);
                xCell(row, 3, nvl(a.status()), isPresent ? preSt : absSt);
            }

            xCell(sh.createRow(4 + rows.size() + 1), 0, "Present: " + p + "   |   Total: " + rows.size(), evenSt);

            for (int i = 0; i < 4; i++)
                sh.autoSizeColumn(i);

            try (FileOutputStream f = new FileOutputStream(out)) {
                wb.write(f);
            }
        }
    }

    private static XSSFCellStyle xStyle(XSSFWorkbook wb, String bgHex, boolean bold, short sz, String fgHex) {
        XSSFCellStyle st = wb.createCellStyle();
        st.setFillForegroundColor(new XSSFColor(hex3(bgHex), null));
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont f = wb.createFont();
        f.setColor(new XSSFColor(hex3(fgHex), null));
        f.setBold(bold);
        f.setFontHeightInPoints(sz);
        st.setFont(f);
        return st;
    }

    private static void xCell(Row row, int col, String val, CellStyle st) {
        org.apache.poi.ss.usermodel.Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        if (st != null)
            c.setCellStyle(st);
    }

    private static byte[] hex3(String h) {
        return new byte[] { (byte) Integer.parseInt(h.substring(0, 2), 16),
                (byte) Integer.parseInt(h.substring(2, 4), 16),
                (byte) Integer.parseInt(h.substring(4, 6), 16) };
    }

    // ══════════════════════════════════════════════════════════
    // FORMAT PICKER + FILE CHOOSER + UTILITIES
    // ══════════════════════════════════════════════════════════

    public static String pickFormat(boolean allowExcel) {
        List<String> ch = allowExcel
                ? List.of("Word (.docx)", "PDF (.pdf)", "Excel (.xlsx)")
                : List.of("Word (.docx)", "PDF (.pdf)");
        return new javafx.scene.control.ChoiceDialog<>(ch.get(0), ch) {
            {
                setTitle("Export Format");
                setHeaderText(null);
                setContentText("Choose export format:");
            }
        }.showAndWait().map(v -> v.contains("Excel") ? "xlsx" : v.contains("PDF") ? "pdf" : "docx").orElse(null);
    }

    private static File chooseSave(String name, String ext) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save");
        fc.setInitialFileName(name + "." + ext);
        fc.getExtensionFilters().add(switch (ext) {
            case "pdf" -> new FileChooser.ExtensionFilter("PDF", "*.pdf");
            case "xlsx" -> new FileChooser.ExtensionFilter("Excel", "*.xlsx");
            default -> new FileChooser.ExtensionFilter("Word", "*.docx");
        });
        return fc.showSaveDialog(new Stage());
    }

    private static String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private static void bg(RunnableEx r) {
        new Thread(() -> {
            try {
                r.run();
            } catch (Exception e) {
                showErr("Export failed: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    @FunctionalInterface
    interface RunnableEx {
        void run() throws Exception;
    }

    private static void showOk(String msg) {
        javafx.application.Platform.runLater(() -> {
            var a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            a.setTitle("Exported");
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        });
    }

    private static void showErr(String msg) {
        javafx.application.Platform.runLater(() -> {
            var a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            a.setTitle("Export Failed");
            a.setHeaderText(null);
            a.setContentText(msg);
            a.showAndWait();
        });
    }
}