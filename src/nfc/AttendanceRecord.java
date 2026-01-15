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
    private final String nfcUid; // ✅ use NFC UID string from Firestore
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

    private final BooleanProperty manualCheckOut = new SimpleBooleanProperty(false);
    private final ObjectProperty<File> reasonLetterFile = new SimpleObjectProperty<>();

    // ─── Constructor ───────────────────────────────────────────────
    public AttendanceRecord(String nfcUid, String name) {
        this.nfcUid = nfcUid;
        this.childId = new SimpleIntegerProperty(nfcUid.hashCode()); // still used internally
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
    public String getNfcUid() { return nfcUid; } // ✅ used for Firestore link
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

    public boolean isManualCheckOut() { return manualCheckOut.get(); }
    public void setManualCheckOut(boolean value) { manualCheckOut.set(value); }
    public BooleanProperty manualCheckOutProperty() { return manualCheckOut; }

    public File getReasonLetterFile() { return reasonLetterFile.get(); }
    public void setReasonLetterFile(File file) { reasonLetterFile.set(file); }
    public ObjectProperty<File> reasonLetterFileProperty() { return reasonLetterFile; }
}