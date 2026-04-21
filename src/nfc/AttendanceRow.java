package nfc;

import java.time.LocalDate;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class AttendanceRow {
    private final StringProperty childId;
    private final StringProperty name;
    private final StringProperty status;
    private final StringProperty reason;
    private final StringProperty checkInTime;
    private final StringProperty checkOutTime;
    private final ObjectProperty<LocalDate> date;
    private final ObjectProperty<LocalDate> checkOutDate; // New: check-out date
    private final StringProperty remark;

    public AttendanceRow(String childId, String name, String status, String reason,
                        String checkInTime, String checkOutTime, LocalDate date) {
        this(childId, name, status, reason, checkInTime, checkOutTime, date, date);
    }

    public AttendanceRow(String childId, String name, String status, String reason,
                        String checkInTime, String checkOutTime, LocalDate date, LocalDate checkOutDate) {
        this.childId = new SimpleStringProperty(childId);
        this.name = new SimpleStringProperty(name);
        this.status = new SimpleStringProperty(status);
        this.reason = new SimpleStringProperty(reason);
        this.checkInTime = new SimpleStringProperty(checkInTime);
        this.checkOutTime = new SimpleStringProperty(checkOutTime);
        this.date = new SimpleObjectProperty<>(date);
        this.checkOutDate = new SimpleObjectProperty<>(checkOutDate);
        this.remark = new SimpleStringProperty("");
    }

    // 🔹 Property Getters
    public StringProperty childIdProperty() { return childId; }
    public StringProperty nameProperty() { return name; }
    public StringProperty statusProperty() { return status; }
    public StringProperty reasonProperty() { return reason; }
    public StringProperty checkInTimeProperty() { return checkInTime; }
    public StringProperty checkOutTimeProperty() { return checkOutTime; }
    public ObjectProperty<LocalDate> dateProperty() { return date; }
    public StringProperty remarkProperty() { return remark; }

    // 🔹 Safe Getters (prevents NullPointerException)
    public String getChildId() { return childId != null ? childId.get() : ""; }
    public String getName() { return name != null ? name.get() : ""; }
    public String getStatus() { return status != null ? status.get() : ""; }
    public String getReason() { return reason != null ? reason.get() : ""; }
    public String getCheckInTime() { return checkInTime != null ? checkInTime.get() : ""; }
    public String getCheckOutTime() { return checkOutTime != null ? checkOutTime.get() : ""; }
    public LocalDate getCheckOutDate() { return checkOutDate != null ? checkOutDate.get() : null; }
    public LocalDate getDate() { return date != null ? date.get() : null; }
    public String getRemark() { return remark != null ? remark.get() : ""; }

    // 🔹 Setters
    public void setRemark(String value) { if (remark != null) remark.set(value); }
}