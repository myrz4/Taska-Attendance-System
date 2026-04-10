Fee Policy Matrix (From Kadar Bayaran TPPM.pdf)

Source date:
- Extracted from Kadar Bayaran TPPM.pdf on 2026-03-18
- Pricing version in code: pdf-2026-03-18

Core monthly fees
- Umur 3 bulan - 2 tahun
  - staff: RM350
  - bukan staff: RM400
  - code: monthly_fulltime_3m_2y
- Umur 2 tahun - 4 tahun
  - staff: RM300
  - bukan staff: RM350
  - code: monthly_fulltime_2y_4y

Transit fees
- Transit 1/2 hari (bulan)
  - staff: RM150
  - bukan staff: RM250
  - code: transit_halfday_month
- Transit 2 jam (bulan)
  - staff: RM100
  - bukan staff: RM180
  - code: transit_2h_month
- Transit penuh cuti sekolah (bulan)
  - staff: RM250
  - bukan staff: RM300
  - code: transit_schoolholiday_month
- Transit 1 hari
  - staff: RM15
  - bukan staff: RM20
  - code: transit_1day
- Transit 1 minggu
  - staff: RM70
  - bukan staff: RM100
  - code: transit_1week
- Transit 1 jam
  - staff: RM3.50
  - bukan staff: RM4.00
  - code: transit_1hour

Overtime fees
- Selepas 5:30 petang
  - staff: RM5/jam
  - bukan staff: RM6/jam
  - code: overtime_after_530
- 8:00 malam - 12:00 malam
  - staff: RM10/jam
  - bukan staff: RM13/jam
  - code: overtime_8pm_12am

Current implemented note
- Overtime is currently billed only for the explicit windows above because those are the active Taska Zurah rules now implemented in the backend.
- Legacy post-midnight pricing code may still exist in older catalog documents, but it is no longer part of the required/default policy set.

Other fees
- Pengangkutan (ambil dari tadika untuk transit)
  - RM150 sebulan
  - code: transport_tadika_month
- Yuran pendaftaran (bayar sekali masa daftar masuk)
  - sepenuh masa: RM100 (code: registration_fulltime_oneoff)
  - transit: RM50 (code: registration_transit_oneoff)
- Yuran tahunan
  - RM100 (code: annual_fee_yearly)
- Buku komunikasi (log harian)
  - RM15 sekali sahaja semasa pendaftaran (code: comms_book_oneoff)
- Insurans
  - RM20 sekali sahaja semasa pendaftaran untuk umur 2 tahun ke atas (code: insurance_oneoff_age2plus)

Rules / terms from PDF
- Pendaftaran + yuran masa daftar tidak dipulangkan.
- Bayaran bulan berikutnya sebelum 5hb atau 7hb.
- Jika tidak hadir tanpa notis bertulis, yuran penuh.
- Potongan 10% jika tidak hadir lebih 14 hari dengan surat.
- Resit bayaran dikeluarkan untuk bayaran yang diterima.

System mapping implemented
- Child fee variables:
  - staffChild (boolean)
  - careType (fulltime or transit variants)
  - registrationType (fulltime/transit)
  - transportFromTadika (boolean)
  - billingDueDay (5 or 7)
- Child registration identity:
  - childIcNo
  - birthCertNo
  - address
- Parent verification:
  - icNo
  - icVerified
  - icVerifiedAt

Invoice generation behavior implemented
- Registration month:
  - adds registration fee on top of the base monthly/transit fee
- Monthly:
  - uses careType + age band + staff/nonstaff
- Generic monthly transit (`careType=transit` or `feePlan=transit`) now resolves from care-duration signals:
  - `<= 2.25` average hours/day or explicit duration => `transit_2h_month`
  - `> 2.25` average hours/day or explicit duration => `transit_halfday_month`
  - school-holiday transit hint => `transit_schoolholiday_month`
- Adds annual fee in January
- Adds communication book only during registration month
- Adds insurance only during registration month if age >= 2 years
- Adds transport if enabled on child profile
- Adds overtime from attendance checkout times (with optional manual override)
- Applies 10% discount when absenceDaysWithLetter > 14 and hasAbsenceLetter=true

Notes
- Overtime split based only on checkout clock time is a practical approximation if no detailed shift segment is stored.

Automated E2E validation status (2026-03-19)
- Runner: teacher_app_taskazurah/functions/e2e-billing-check.js
- Command: npm run e2e:billing (from teacher_app_taskazurah/functions)
- Result: PASS

Validated scenarios
- Registration month stacks monthly base + registration fee (and due day 5 works).
- Transit monthly fee + overtime + transport + >14-day letter discount (and due day 7 works).
- Fee catalog callable exposes policy due-day options [5, 7].
- Salary configuration callable returns stored salary rates for current teacher identity.
- January policy items are added correctly: annual fee only, without repeating registration-only communication book or insurance.