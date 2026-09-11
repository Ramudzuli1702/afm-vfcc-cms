package com.afmvfcc.controllers;

import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.Member;

import java.sql.Connection;
import java.sql.ResultSet;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MemberPdfExporter {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public static void export(List<Member> ignored) {
        exportAll();
    }

    public static void exportAll() {
        List<DocumentExporter.MemberRow> rows = new ArrayList<>();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT m.*, " +
                "sb.name AS sub_branch_name, " +
                "IFNULL(GROUP_CONCAT(mi.name ORDER BY mi.name SEPARATOR ', '), '\u2014') AS ministries, " +
                "spouse.full_name AS spouse_name " +
                "FROM members m " +
                "LEFT JOIN sub_branches sb ON sb.id = m.sub_branch_id " +
                "LEFT JOIN member_ministries mm ON mm.member_id = m.id " +
                "LEFT JOIN ministries mi ON mi.id = mm.ministry_id " +
                "LEFT JOIN members spouse ON spouse.id = m.spouse_member_id " +
                "WHERE m.is_deleted = 0 AND m.is_deceased = 0 " +
                "GROUP BY m.id, m.full_name, m.phone, m.sub_branch_id, m.is_full_time, " +
                "m.is_active, m.date_joined, m.marital_status, m.employment_status, " +
                "m.is_spouse_member, spouse.full_name " +
                "ORDER BY m.full_name ASC"
            );

            while (rs.next()) {
                String dateJoined = rs.getDate("date_joined") != null
                        ? rs.getDate("date_joined").toLocalDate().format(FMT) : "\u2014";

                boolean fullTime = rs.getInt("is_full_time") == 1;
                boolean active = rs.getInt("is_active") == 1;

                String maritalStatus = rs.getString("marital_status") != null ? rs.getString("marital_status") : "Single";
                String employmentStatus = rs.getString("employment_status") != null ? rs.getString("employment_status") : "Employed";

                String spouseInfo = "\u2014";
                if ("Married".equals(maritalStatus)) {
                    boolean isSpouseMember = rs.getInt("is_spouse_member") == 1;
                    String spouseName = rs.getString("spouse_name");
                    if (isSpouseMember && spouseName != null) {
                        spouseInfo = "Yes - " + spouseName;
                    } else {
                        spouseInfo = "Yes (Spouse not in system)";
                    }
                }

                rows.add(new DocumentExporter.MemberRow(
                        rs.getString("full_name"),
                        rs.getString("phone") != null ? rs.getString("phone") : "\u2014",
                        rs.getString("sub_branch_name") != null ? rs.getString("sub_branch_name") : "\u2014",
                        rs.getString("ministries"),
                        fullTime ? "Full Time" : "Part Time",
                        active ? "Active" : "Inactive",
                        dateJoined,
                        maritalStatus,
                        employmentStatus,
                        spouseInfo
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        DocumentExporter.exportMembers(rows);
    }
}