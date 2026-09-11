package com.afmvfcc.controllers;

import com.afmvfcc.models.WelfareCase;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class WelfarePdfExporter {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public static void export(List<WelfareCase> cases) {
        List<DocumentExporter.WelfareRow> rows = cases.stream()
            .map(wc -> new DocumentExporter.WelfareRow(
                wc.getMemberName(),
                wc.getReason()             != null ? wc.getReason()             : "\u2014",
                wc.getAssignedWorkerName() != null ? wc.getAssignedWorkerName() : "Unassigned",
                wc.getReport()             != null ? wc.getReport()             : "\u2014",
                wc.getStatus(),
                wc.getOpenedAt()           != null
                    ? wc.getOpenedAt().toLocalDate().format(FMT) : "\u2014"
            ))
            .collect(Collectors.toList());
        DocumentExporter.exportWelfare(rows);
    }
}
