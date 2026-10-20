package com.library.model;

import jakarta.persistence.*;
import java.util.List;

/** A student's request to borrow a book, and its life cycle. */
@Entity
@Table(name = "loans")
public class Loan {
    // status: REQUESTED -> ISSUED -> RETURNED   (or DECLINED)
    public static final List<String> ACTIVE = List.of("REQUESTED", "ISSUED");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public Long bookId;
    public String bookTitle;
    public String studentId;
    public String status;
    public int days;                 // how long the student asked to keep the book
    public long requestedAt;
    public Long dueAt;
    public Long returnedAt;
    public boolean returnRequested;  // student pressed "Return book", waiting for the librarian

    @Transient public String studentName;
    @Transient public String studentSchool;
    @Transient public String studentPhoto;
}
