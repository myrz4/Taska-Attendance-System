#line 1 "C:\\Users\\zafri\\Downloads\\Taska Attendance System\\TaskaNFCAttendance\\AttendanceDocCandidate.h"
#ifndef ATTENDANCE_DOC_CANDIDATE_H
#define ATTENDANCE_DOC_CANDIDATE_H

struct AttendanceDocCandidate {
  bool exists;
  String docId;
  String docPath;
  String payload;
  bool hasCheckIn;
  bool hasCheckOut;
  bool isAdminCorrected;
  String status;
  String sortKey;
};

#endif