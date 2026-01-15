package nfc;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class StudentMonthlyAttendance {
    private final StringProperty childId;
    private final StringProperty name;
    private final StringProperty attendancePercent;
    private final StringProperty performance;
    private final StringProperty parentName;
    private final StringProperty parentContact;

    public StudentMonthlyAttendance(String childId, String name, String attendancePercent, String performance) {
        this.childId = new SimpleStringProperty(childId);
        this.name = new SimpleStringProperty(name);
        this.attendancePercent = new SimpleStringProperty(attendancePercent);
        this.performance = new SimpleStringProperty(performance);
        this.parentName = new SimpleStringProperty("");
        this.parentContact = new SimpleStringProperty("");
    }

    public StringProperty childIdProperty() {
        return childId;
    }

    public String getChildId() {
        return childId.get();
    }

    public void setChildId(String id) {
        childId.set(id);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getName() {
        return name.get();
    }

    public StringProperty attendancePercentProperty() {
        return attendancePercent;
    }

    public String getAttendancePercent() {
        return attendancePercent.get();
    }

    public StringProperty performanceProperty() {
        return performance;
    }

    public String getPerformance() {
        return performance.get();
    }

    public StringProperty parentNameProperty() { return parentName; }
    public StringProperty parentContactProperty() { return parentContact; }

    public String getParentName() { return parentName.get(); }
    public String getParentContact() { return parentContact.get(); }

    public void setParentName(String value) { parentName.set(value); }
    public void setParentContact(String value) { parentContact.set(value); }
}