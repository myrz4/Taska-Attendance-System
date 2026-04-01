package nfc;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class AttendanceRecord {
    // ─── Core fields ───────────────────────────────────────────────
    private final String childDocId; // stable Firestore children/{childId} docId
    private final String nfcUid; // replaceable NFC UID (children.nfc_uid)
    private final IntegerProperty childId;
    private final StringProperty name;

    // ─── Attendance info ───────────────────────────────────────────
    private final BooleanProperty present = new SimpleBooleanProperty(false);
    private final StringProperty reason = new SimpleStringProperty("Default");
    private final StringProperty customReason = new SimpleStringProperty("");

    private final StringProperty checkInFullTimestamp = new SimpleStringProperty("");
    private final StringProperty checkInTime = new SimpleStringProperty("");
    private final StringProperty checkOutFullTimestamp = new SimpleStringProperty("");
    private final StringProperty checkOutTime = new SimpleStringProperty("");
    private final StringProperty checkInMethod = new SimpleStringProperty("");
    private final StringProperty checkOutMethod = new SimpleStringProperty("");
    private final StringProperty manualEditReason = new SimpleStringProperty("");
    private final StringProperty updatedBy = new SimpleStringProperty("");

    private final BooleanProperty manualCheckOut = new SimpleBooleanProperty(false);
    private final ObjectProperty<File> reasonLetterFile = new SimpleObjectProperty<>();

    // ─── Constructor ───────────────────────────────────────────────
    public AttendanceRecord(String childDocId, String name) {
        this(childDocId, name, "");
    }

    public AttendanceRecord(String childDocId, String name, String nfcUid) {
        this.childDocId = childDocId;
        this.nfcUid = nfcUid == null ? "" : nfcUid;
        this.childId = new SimpleIntegerProperty((childDocId == null ? "" : childDocId).hashCode()); // still used internally
        this.name = new SimpleStringProperty(name);

        // Auto time tracking
        this.present.addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                setCheckInFullTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } else {
                setCheckInFullTimestamp("");
            }
        });

        this.manualCheckOut.addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                setCheckOutFullTimestamp(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } else {
                setCheckOutFullTimestamp("");
            }
        });
    }

    // ─── Getters/Properties ────────────────────────────────────────
    public String getChildDocId() { return childDocId; } // ✅ used for Firestore link
    public String getNfcUid() { return nfcUid; }
    public int getChildId() { return childId.get(); }
    public IntegerProperty childIdProperty() { return childId; }

    public String getName() { return name.get(); }
    public StringProperty nameProperty() { return name; }

    public boolean isPresent() { return present.get(); }
    public void setPresent(boolean value) { present.set(value); }
    public BooleanProperty presentProperty() { return present; }

    public String getReason() { return reason.get(); }
    public void setReason(String value) { reason.set(value); }
    public StringProperty reasonProperty() { return reason; }

    public String getCustomReason() { return customReason.get(); }
    public void setCustomReason(String value) { customReason.set(value); }
    public StringProperty customReasonProperty() { return customReason; }

    public String getCheckInFullTimestamp() { return checkInFullTimestamp.get(); }
    public void setCheckInFullTimestamp(String value) {
        checkInFullTimestamp.set(value);
        if (value != null && !value.isEmpty()) {
            LocalDateTime dt = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            checkInTime.set(dt.format(DateTimeFormatter.ofPattern("hh:mm a")));
        } else {
            checkInTime.set("");
        }
    }
    public StringProperty checkInFullTimestampProperty() { return checkInFullTimestamp; }

    public String getCheckInTime() { return checkInTime.get(); }
    public StringProperty checkInTimeProperty() { return checkInTime; }

    public String getCheckOutFullTimestamp() { return checkOutFullTimestamp.get(); }
    public void setCheckOutFullTimestamp(String value) {
        checkOutFullTimestamp.set(value);
        if (value != null && !value.isEmpty()) {
            LocalDateTime dt = LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            checkOutTime.set(dt.format(DateTimeFormatter.ofPattern("hh:mm a")));
        } else {
            checkOutTime.set("");
        }
    }
    public StringProperty checkOutFullTimestampProperty() { return checkOutFullTimestamp; }

    public String getCheckOutTime() { return checkOutTime.get(); }
    public StringProperty checkOutTimeProperty() { return checkOutTime; }

    public String getCheckInMethod() { return checkInMethod.get(); }
    public void setCheckInMethod(String value) { checkInMethod.set(value == null ? "" : value); }
    public StringProperty checkInMethodProperty() { return checkInMethod; }

    public String getCheckOutMethod() { return checkOutMethod.get(); }
    public void setCheckOutMethod(String value) { checkOutMethod.set(value == null ? "" : value); }
    public StringProperty checkOutMethodProperty() { return checkOutMethod; }

    public String getManualEditReason() { return manualEditReason.get(); }
    public void setManualEditReason(String value) { manualEditReason.set(value == null ? "" : value); }
    public StringProperty manualEditReasonProperty() { return manualEditReason; }

    public String getUpdatedBy() { return updatedBy.get(); }
    public void setUpdatedBy(String value) { updatedBy.set(value == null ? "" : value); }
    public StringProperty updatedByProperty() { return updatedBy; }

    public boolean isManualCheckOut() { return manualCheckOut.get(); }
    public void setManualCheckOut(boolean value) { manualCheckOut.set(value); }
    public BooleanProperty manualCheckOutProperty() { return manualCheckOut; }

    public boolean hasCheckIn() {
        return getCheckInFullTimestamp() != null && !getCheckInFullTimestamp().isBlank();
    }

    public boolean hasCheckOut() {
        return getCheckOutFullTimestamp() != null && !getCheckOutFullTimestamp().isBlank();
    }

    public boolean isAdminCorrected() {
        return !getManualEditReason().isBlank() || getSourceSummary().toLowerCase().contains("admin manual");
    }

    public String getSourceSummary() {
        StringBuilder summary = new StringBuilder();
        String inLabel = methodLabel(getCheckInMethod(), false);
        String outLabel = methodLabel(getCheckOutMethod(), true);
        if (!inLabel.isBlank()) {
            summary.append("Check-in via ").append(inLabel);
        }
        if (!outLabel.isBlank()) {
            if (summary.length() > 0) {
                summary.append(" | ");
            }
            summary.append("Check-out via ").append(outLabel);
        }
        return summary.length() == 0 ? "Attendance source not recorded yet" : summary.toString();
    }

    private String methodLabel(String value, boolean isCheckout) {
        if (value == null) {
            return "";
        }
        switch (value.trim().toUpperCase()) {
            case "NFC":
                return isCheckout ? "NFC scan" : "NFC tap";
            case "QR":
            case "PARENT_QR":
                return "Parent QR";
            case "MANUAL":
            case "ADMIN_MANUAL":
                return "Admin manual";
            default:
                return "";
        }
    }

    public String getStatusLabel() {
        if (hasCheckOut()) {
            return "Checked Out";
        }
        if (hasCheckIn()) {
            return "Checked In";
        }
        if (getReason() != null && !getReason().isBlank() && !"Default".equalsIgnoreCase(getReason())) {
            return "Absent";
        }
        return "Not Checked In";
    }

    public File getReasonLetterFile() { return reasonLetterFile.get(); }
    public void setReasonLetterFile(File file) { reasonLetterFile.set(file); }
    public ObjectProperty<File> reasonLetterFileProperty() { return reasonLetterFile; }
}