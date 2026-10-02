package com.afmvfcc.controllers;

import com.afmvfcc.db.DatabaseConnection;
import com.afmvfcc.models.Member;
import com.afmvfcc.utils.AuditLogger;
import com.afmvfcc.utils.Avatars;
import com.afmvfcc.utils.SessionManager;
import com.afmvfcc.utils.ToastManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AddEditMemberController {

    @FXML private Label dialogTitle;
    @FXML private TextField fullNameField, phoneField, emailField;
    @FXML private TextField nokNameField, nokPhoneField;
    @FXML private TextArea addressField;

    @FXML private ComboBox<String> genderCombo, maritalStatusCombo, employmentCombo,
                                   subBranchCombo, availabilityCombo, statusCombo, familyCombo;

    @FXML private DatePicker dobPicker, baptismPicker, joinedPicker;

    // Spouse section — shown only when Married
    @FXML private VBox     spouseSection;
    @FXML private HBox     spouseCheckBox;
    @FXML private CheckBox spouseIsMemberCheck;
    @FXML private VBox     spouseSelectBox;
    @FXML private ComboBox<String> spouseCombo;

    // Photo
    @FXML private StackPane photoHolder;
    @FXML private Label     photoNameLabel;
    @FXML private Button    removePhotoBtn;

    @FXML private FlowPane   ministriesBox;
    @FXML private Label      errorLabel;
    @FXML private HBox       footerBox;
    @FXML private ScrollPane bodyScroll;

    private Member   editingMember = null;
    private Runnable onSaved;

    /** Photo currently stored for the member (null = none). */
    private String photoPath;
    /** A newly chosen file, copied into the photo folder only when Save is clicked. */
    private File   pendingPhoto;

    private final List<int[]>    ministryIds    = new ArrayList<>();
    private final List<CheckBox> ministryChecks = new ArrayList<>();
    private final Map<String, Integer> spouseIds = new LinkedHashMap<>();

    @FXML
    public void initialize() {
        genderCombo.getItems().addAll("Male", "Female");
        maritalStatusCombo.getItems().addAll("Single", "Married", "Divorced", "Widowed");
        employmentCombo.getItems().addAll("Employed", "Student", "Retired", "Unemployed");
        availabilityCombo.getItems().addAll("Full Time", "Part Time");
        statusCombo.getItems().addAll("Active", "Inactive");

        availabilityCombo.setValue("Full Time");
        statusCombo.setValue("Active");
        maritalStatusCombo.setValue("Single");
        employmentCombo.setValue("Employed");

        // Show/hide spouse section when marital status changes
        maritalStatusCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isMarried = "Married".equals(newVal);
            spouseSection.setVisible(isMarried);
            spouseSection.setManaged(isMarried);
            if (!isMarried) spouseIsMemberCheck.setSelected(false);
        });

        // Show spouse picker only when checkbox is ticked
        spouseIsMemberCheck.selectedProperty().addListener((obs, was, isNow) -> {
            spouseSelectBox.setVisible(isNow);
            spouseSelectBox.setManaged(isNow);
        });

        // Keep the name under the photo in step with the form
        fullNameField.textProperty().addListener((o, a, b) ->
            photoNameLabel.setText(b == null || b.isBlank() ? "New member" : b.trim()));

        loadSubBranches();
        loadMinistries();
        loadFamilies();
        loadSpouseCandidates();
        refreshPhoto();
    }

    /** The body scroller, so the opener can size the window to fit the form. */
    public ScrollPane getBodyScroll() { return bodyScroll; }

    public void setMember(Member member) {
        this.editingMember = member;

        if (member == null) {
            dialogTitle.setText("Add New Member");
            return;
        }

        dialogTitle.setText("Edit Member");

        fullNameField.setText (member.getFullName()       != null ? member.getFullName()       : "");
        phoneField.setText    (member.getPhone()          != null ? member.getPhone()          : "");
        emailField.setText    (member.getEmail()          != null ? member.getEmail()          : "");
        addressField.setText  (member.getAddress()        != null ? member.getAddress()        : "");
        nokNameField.setText  (member.getNextOfKinName()  != null ? member.getNextOfKinName()  : "");
        nokPhoneField.setText (member.getNextOfKinPhone() != null ? member.getNextOfKinPhone() : "");

        genderCombo.setValue(member.getGender());
        maritalStatusCombo.setValue(member.getMaritalStatus());
        employmentCombo.setValue(member.getEmploymentStatus());
        availabilityCombo.setValue(member.isFullTime() ? "Full Time" : "Part Time");
        statusCombo.setValue(member.isActive() ? "Active" : "Inactive");

        if (member.getDateOfBirth()  != null) dobPicker.setValue(member.getDateOfBirth());
        if (member.getBaptismDate()  != null) baptismPicker.setValue(member.getBaptismDate());
        if (member.getDateJoined()   != null) joinedPicker.setValue(member.getDateJoined());

        loadSelectedSubBranch(member.getSubBranchId());
        loadSelectedFamily(member.getFamilyId());
        loadSelectedMinistries(member.getId());

        boolean married = "Married".equals(member.getMaritalStatus());
        spouseSection.setVisible(married);
        spouseSection.setManaged(married);
        spouseIsMemberCheck.setSelected(married && member.isSpouseMember());
        if (member.getSpouseMemberId() != null)
            spouseIds.forEach((name, id) -> { if (id.equals(member.getSpouseMemberId())) spouseCombo.setValue(name); });
        // A member can't be their own spouse
        spouseIds.entrySet().removeIf(en -> en.getValue() == member.getId());
        spouseCombo.getItems().setAll(spouseIds.keySet());

        photoPath = member.getPhotoPath();
        refreshPhoto();
    }

    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    // ── PHOTO ──────────────────────────────────────────────────

    @FXML
    public void handleChoosePhoto() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose Member Photo");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Images (*.jpg, *.jpeg, *.png)", "*.jpg", "*.jpeg", "*.png"));
        File f = fc.showOpenDialog(fullNameField.getScene().getWindow());
        if (f != null) {
            pendingPhoto = f;
            refreshPhoto();
        }
    }

    @FXML
    public void handleRemovePhoto() {
        pendingPhoto = null;
        photoPath = null;
        refreshPhoto();
    }

    private void refreshPhoto() {
        String shown = pendingPhoto != null ? pendingPhoto.getAbsolutePath() : photoPath;
        photoHolder.getChildren().setAll(Avatars.of(shown, 140));
        boolean has = shown != null && !shown.isBlank();
        removePhotoBtn.setVisible(has);
        removePhotoBtn.setManaged(has);
    }

    // ── SAVE ───────────────────────────────────────────────────

    @FXML
    public void handleSave() {
        clearError();

        String fullName = fullNameField.getText().trim();
        if (fullName.isEmpty()) { showError("Full name is required."); return; }

        String subBranch = subBranchCombo.getValue();
        if (subBranch == null || subBranch.isEmpty()) { showError("Sub-branch is required."); return; }

        String spouse = spouseCombo.getValue();
        if ("Married".equals(maritalStatusCombo.getValue()) && spouseIsMemberCheck.isSelected()
                && (spouse == null || !spouseIds.containsKey(spouse))) {
            showError("Please select the spouse from the list, or untick \"Spouse is also a member\".");
            return;
        }

        if (pendingPhoto != null) {
            try {
                photoPath = Avatars.storePhoto(pendingPhoto, fullName);
                pendingPhoto = null;
            } catch (IOException e) {
                showError("Could not save the photo: " + e.getMessage());
                return;
            }
        }

        boolean isNewMember = editingMember == null;
        try {
            Connection conn = DatabaseConnection.getConnection();
            int subBranchId = getSubBranchId(conn, subBranch);
            if (isNewMember) insertMember(conn, fullName, subBranchId);
            else             updateMember(conn, fullName, subBranchId);
            if (onSaved != null) onSaved.run();
            closeDialog();
            ToastManager.success(isNewMember ? "Member added successfully." : "Member updated successfully.");
        } catch (SQLException e) {
            showError("Database error: " + e.getMessage());
            e.printStackTrace();
            ToastManager.error("Failed to save member: " + e.getMessage());
        }
    }

    private void insertMember(Connection conn, String fullName, int subBranchId) throws SQLException {
        String sql = """
                INSERT INTO members
                (full_name, gender, marital_status, employment_status, is_spouse_member, spouse_member_id,
                 date_of_birth, phone, email, address, sub_branch_id, baptism_date, date_joined,
                 next_of_kin_name, next_of_kin_phone, family_id, is_full_time, is_active, photo_path)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        fillStatement(ps, fullName, subBranchId);
        ps.executeUpdate();
        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) {
            int newId = keys.getInt(1);
            saveMinistries(conn, newId);
            if (editingMember == null) editingMember = new Member();
            editingMember.setId(newId);
            updateEditingMemberFromUI(fullName);
            AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                    "Added new member: " + fullName + " (ID " + newId + ")");
        }
    }

    private void updateMember(Connection conn, String fullName, int subBranchId) throws SQLException {
        String sql = """
                UPDATE members SET
                 full_name=?, gender=?, marital_status=?, employment_status=?, is_spouse_member=?, spouse_member_id=?,
                 date_of_birth=?, phone=?, email=?, address=?, sub_branch_id=?, baptism_date=?, date_joined=?,
                 next_of_kin_name=?, next_of_kin_phone=?, family_id=?, is_full_time=?, is_active=?, photo_path=?
                WHERE id=?
                """;
        PreparedStatement ps = conn.prepareStatement(sql);
        fillStatement(ps, fullName, subBranchId);
        ps.setInt(20, editingMember.getId());
        ps.executeUpdate();
        saveMinistries(conn, editingMember.getId());
        updateEditingMemberFromUI(fullName);
        AuditLogger.log(SessionManager.getInstance().getCurrentUser().getId(),
                "Updated member: " + fullName + " (ID " + editingMember.getId() + ")");
    }

    private void fillStatement(PreparedStatement ps, String fullName, int subBranchId) throws SQLException {
        int idx = 1;
        ps.setString(idx++, fullName);
        ps.setString(idx++, genderCombo.getValue());
        ps.setString(idx++, maritalStatusCombo.getValue());
        ps.setString(idx++, employmentCombo.getValue());

        Integer spouseId = selectedSpouseId();
        ps.setInt(idx++, spouseId != null ? 1 : 0);
        if (spouseId != null) ps.setInt(idx++, spouseId); else ps.setNull(idx++, Types.INTEGER);

        ps.setDate  (idx++, dobPicker.getValue()     != null ? Date.valueOf(dobPicker.getValue())     : null);
        ps.setString(idx++, phoneField.getText().trim());
        ps.setString(idx++, emailField.getText().trim());
        ps.setString(idx++, addressField.getText().trim());
        ps.setInt   (idx++, subBranchId);
        ps.setDate  (idx++, baptismPicker.getValue() != null ? Date.valueOf(baptismPicker.getValue()) : null);
        ps.setDate  (idx++, joinedPicker.getValue()  != null ? Date.valueOf(joinedPicker.getValue())  : null);
        ps.setString(idx++, nokNameField.getText().trim());
        ps.setString(idx++, nokPhoneField.getText().trim());

        String family = familyCombo.getValue();
        if (family == null || "None".equals(family)) {
            ps.setNull(idx++, Types.INTEGER);
        } else {
            try { ps.setInt(idx++, getFamilyId(DatabaseConnection.getConnection(), family)); }
            catch (Exception e) { ps.setNull(idx++, Types.INTEGER); }
        }

        ps.setInt(idx++, "Full Time".equals(availabilityCombo.getValue()) ? 1 : 0);
        ps.setInt(idx++, "Active".equals(statusCombo.getValue()) ? 1 : 0);
        ps.setString(idx++, photoPath);
    }

    /** The chosen spouse's member id, or null if not married to a member. */
    private Integer selectedSpouseId() {
        boolean isMarried = "Married".equals(maritalStatusCombo.getValue());
        if (!isMarried || !spouseIsMemberCheck.isSelected()) return null;
        return spouseIds.get(spouseCombo.getValue());
    }

    private void updateEditingMemberFromUI(String fullName) {
        editingMember.setFullName(fullName);
        editingMember.setPhone(phoneField.getText().trim());
        editingMember.setEmail(emailField.getText().trim());
        editingMember.setGender(genderCombo.getValue());
        editingMember.setMaritalStatus(maritalStatusCombo.getValue());
        editingMember.setEmploymentStatus(employmentCombo.getValue());
        editingMember.setSpouseMember(selectedSpouseId() != null);
        editingMember.setSpouseMemberId(selectedSpouseId());
        editingMember.setAddress(addressField.getText().trim());
        editingMember.setNextOfKinName(nokNameField.getText().trim());
        editingMember.setNextOfKinPhone(nokPhoneField.getText().trim());
        editingMember.setFullTime("Full Time".equals(availabilityCombo.getValue()));
        editingMember.setActive("Active".equals(statusCombo.getValue()));
        editingMember.setPhotoPath(photoPath);
        if (dobPicker.getValue()     != null) editingMember.setDateOfBirth(dobPicker.getValue());
        if (baptismPicker.getValue() != null) editingMember.setBaptismDate(baptismPicker.getValue());
        if (joinedPicker.getValue()  != null) editingMember.setDateJoined(joinedPicker.getValue());
    }

    private void saveMinistries(Connection conn, int memberId) throws SQLException {
        conn.createStatement().executeUpdate("DELETE FROM member_ministries WHERE member_id = " + memberId);
        for (int i = 0; i < ministryChecks.size(); i++) {
            if (ministryChecks.get(i).isSelected()) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO member_ministries (member_id, ministry_id) VALUES (?,?)");
                ps.setInt(1, memberId);
                ps.setInt(2, ministryIds.get(i)[0]);
                ps.executeUpdate();
            }
        }
    }

    // ── LOADERS ────────────────────────────────────────────────

    private void loadSubBranches() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT name FROM sub_branches WHERE is_active=1 ORDER BY name");
            while (rs.next()) subBranchCombo.getItems().add(rs.getString("name"));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadMinistries() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT id, name FROM ministries WHERE is_active=1 ORDER BY name");
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                CheckBox cb = new CheckBox(name);
                ministryIds.add(new int[]{id});
                ministryChecks.add(cb);
                ministriesBox.getChildren().add(cb);
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadFamilies() {
        familyCombo.getItems().add("None");
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT family_name FROM families ORDER BY family_name");
            while (rs.next()) familyCombo.getItems().add(rs.getString("family_name"));
        } catch (SQLException e) { e.printStackTrace(); }
        familyCombo.setValue("None");
    }

    private void loadSpouseCandidates() {
        spouseIds.clear();
        try {
            Connection conn = DatabaseConnection.getConnection();
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT id, full_name FROM members WHERE is_deleted=0 AND is_deceased=0 ORDER BY full_name");
            while (rs.next()) spouseIds.put(rs.getString("full_name"), rs.getInt("id"));
        } catch (SQLException e) { e.printStackTrace(); }
        spouseCombo.getItems().setAll(spouseIds.keySet());
    }

    private void loadSelectedSubBranch(int subBranchId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT name FROM sub_branches WHERE id=?");
            ps.setInt(1, subBranchId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) subBranchCombo.setValue(rs.getString("name"));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadSelectedFamily(Integer familyId) {
        if (familyId == null) { familyCombo.setValue("None"); return; }
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement("SELECT family_name FROM families WHERE id=?");
            ps.setInt(1, familyId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) familyCombo.setValue(rs.getString("family_name"));
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private void loadSelectedMinistries(int memberId) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT ministry_id FROM member_ministries WHERE member_id=?");
            ps.setInt(1, memberId);
            ResultSet rs = ps.executeQuery();
            List<Integer> selected = new ArrayList<>();
            while (rs.next()) selected.add(rs.getInt("ministry_id"));
            for (int i = 0; i < ministryIds.size(); i++)
                if (selected.contains(ministryIds.get(i)[0]))
                    ministryChecks.get(i).setSelected(true);
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private int getSubBranchId(Connection conn, String name) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT id FROM sub_branches WHERE name=?");
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt("id") : 1;
    }

    private int getFamilyId(Connection conn, String name) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT id FROM families WHERE family_name=?");
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getInt("id") : 0;
    }

    // ── UI HELPERS ─────────────────────────────────────────────

    @FXML
    public void handlePrintForm() {
        if (editingMember == null || editingMember.getId() <= 0) {
            Alert a = new Alert(Alert.AlertType.WARNING);
            a.setTitle("Print Form"); a.setHeaderText(null);
            a.setContentText("Please save the member first before printing the form.");
            a.showAndWait();
            return;
        }
        DocumentExporter.exportMemberForm(editingMember);
    }

    @FXML public void handleCancel() { closeDialog(); }

    private void closeDialog() {
        ((Stage) fullNameField.getScene().getWindow()).close();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
