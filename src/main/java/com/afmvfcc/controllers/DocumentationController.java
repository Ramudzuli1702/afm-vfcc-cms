package com.afmvfcc.controllers;

import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public class DocumentationController {

    @FXML private VBox userGuideContentBox;
    @FXML private VBox policyContentBox;

    @FXML
    public void initialize() {
        for (UserGuideContent.GuideSection s : UserGuideContent.sections()) {
            userGuideContentBox.getChildren().add(renderSection(s.heading(), s.body(), "#1A2B4A"));
        }
        for (PolicyContent.PolicySection s : PolicyContent.sections()) {
            policyContentBox.getChildren().add(renderSection(s.heading(), s.body(), "#1E2130"));
        }
    }

    /** Renders one Guide/Policy section (heading + paragraphs/bullets) as a VBox, shared by both tabs. */
    private VBox renderSection(String heading, List<String> body, String headingColor) {
        VBox section = new VBox(8);

        Label headingLabel = new Label(heading);
        headingLabel.setWrapText(true);
        headingLabel.setStyle(
            "-fx-font-size:16px;-fx-font-weight:700;-fx-text-fill:" + headingColor + ";" +
            "-fx-padding:0 0 6 0;-fx-border-color:transparent transparent #DDE1EA transparent;" +
            "-fx-border-width:0 0 1 0;");
        headingLabel.setMaxWidth(Double.MAX_VALUE);
        section.getChildren().add(headingLabel);

        for (String line : body) {
            boolean bullet = line.startsWith("- ");
            if (bullet) {
                HBox row = new HBox(8);
                Label dot = new Label("•");
                dot.setStyle("-fx-font-size:13px;-fx-text-fill:#5A6275;");
                Label text = new Label(line.substring(2));
                text.setWrapText(true);
                text.setStyle("-fx-font-size:13px;-fx-text-fill:#1E2130;-fx-line-spacing:2;");
                HBox.setHgrow(text, Priority.ALWAYS);
                text.setMaxWidth(Double.MAX_VALUE);
                row.getChildren().addAll(dot, text);
                row.setPadding(new Insets(0, 0, 0, 6));
                section.getChildren().add(row);
            } else {
                Label text = new Label(line);
                text.setWrapText(true);
                text.setStyle("-fx-font-size:13px;-fx-text-fill:#1E2130;-fx-line-spacing:2;");
                text.setMaxWidth(Double.MAX_VALUE);
                section.getChildren().add(text);
            }
        }
        return section;
    }

    @FXML
    public void handleExportUserGuide() {
        DocumentExporter.exportUserGuide();
        AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(), "Exported the User Guide");
    }

    @FXML
    public void handleExportPolicy() {
        DocumentExporter.exportPolicy();
        AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(), "Exported the System Usage Policy");
    }
}
