package com.library.service;

import com.library.model.Loan;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/** Calculates the numbers shown on the profile and dashboard cards. */
@Service
public class StatsService {
    private static final double DAY = 86_400_000.0;
    private final LoanRepository loans;
    private final BookRepository books;

    public StatsService(LoanRepository loans, BookRepository books) {
        this.loans = loans;
        this.books = books;
    }

    /** Reading record of one student: how often and how early they return books. */
    public Map<String, Object> student(String studentId) {
        long now = System.currentTimeMillis();
        int total = 0, returned = 0, late = 0, overdue = 0;
        double sumEarlyDays = 0;
        for (Loan l : loans.findByStudentId(studentId)) {
            boolean issued = "ISSUED".equals(l.status), done = "RETURNED".equals(l.status);
            if (!issued && !done) continue;
            total++;
            if (done && l.dueAt != null && l.returnedAt != null) {
                returned++;
                if (l.returnedAt > l.dueAt) late++;
                sumEarlyDays += (l.dueAt - l.returnedAt) / DAY;   // negative = late
            }
            if (issued && l.dueAt != null && l.dueAt < now) overdue++;
        }
        Integer onTimeRate = returned == 0 ? null : (int) Math.round((returned - late) * 100.0 / returned);
        String grade;
        if (returned == 0 && overdue == 0) grade = "New borrower";
        else if (overdue == 0 && onTimeRate != null && onTimeRate >= 90) grade = "Excellent";
        else if (onTimeRate != null && onTimeRate >= 70 && overdue <= 1) grade = "Good";
        else grade = "Needs attention";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("returned", returned);
        m.put("lateReturns", late);
        m.put("overdueNow", overdue);
        m.put("onTimeRate", onTimeRate);
        m.put("avgDays", returned == 0 ? 0 : sumEarlyDays / returned);
        m.put("grade", grade);
        return m;
    }

    /** Numbers for the whole library. */
    public Map<String, Object> library() {
        int issued = 0, returned = 0, declined = 0, requests = 0;
        for (Loan l : loans.findAll()) {
            switch (l.status) {
                case "ISSUED" -> issued++;
                case "RETURNED" -> { issued++; returned++; }
                case "DECLINED" -> declined++;
                case "REQUESTED" -> requests++;
                default -> { }
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issued", issued);
        m.put("requests", requests);
        m.put("completion", issued == 0 ? null : (int) Math.round(returned * 100.0 / issued));
        m.put("approval", issued + declined == 0 ? null : (int) Math.round(issued * 100.0 / (issued + declined)));
        m.put("titles", books.count());
        return m;
    }
}
