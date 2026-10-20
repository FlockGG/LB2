package com.library.controller;

import com.library.model.Account;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.repository.AccountRepository;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import com.library.service.AuthService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/** Borrowing workflow: request -> issue -> return request -> confirm return. */
@RestController
@RequestMapping("/api/loans")
public class LoanController {
    private static final long DAY = 86_400_000L;
    private final LoanRepository loans;
    private final BookRepository books;
    private final AccountRepository accounts;
    private final AuthService auth;

    public LoanController(LoanRepository loans, BookRepository books, AccountRepository accounts, AuthService auth) {
        this.loans = loans;
        this.books = books;
        this.accounts = accounts;
        this.auth = auth;
    }

    public record BorrowRequest(Long bookId, int days) {}

    /** Librarian: every loan, with the student's name attached. */
    @GetMapping
    public List<Loan> all(@RequestHeader(value = "X-Token", required = false) String token) {
        auth.require(token, "lib");
        List<Loan> list = loans.findAll(Sort.by(Sort.Direction.DESC, "id"));
        Map<String, Account> byId = new HashMap<>();
        accounts.findAllById(list.stream().map(l -> l.studentId).collect(Collectors.toSet()))
                .forEach(a -> byId.put(a.id, a));
        for (Loan l : list) {
            Account a = byId.get(l.studentId);
            l.studentName = (a == null || a.name == null || a.name.isBlank()) ? "Student" : a.name;
            if (a != null) { l.studentSchool = a.school; l.studentPhoto = a.photo; }
        }
        return list;
    }

    /** Student: only my own loans. */
    @GetMapping("/mine")
    public List<Loan> mine(@RequestHeader(value = "X-Token", required = false) String token) {
        Account me = auth.require(token, "stu");
        List<Loan> list = loans.findByStudentId(me.id);
        list.sort((a, b) -> Long.compare(b.requestedAt, a.requestedAt));
        return list;
    }

    @PostMapping
    public Loan request(@RequestHeader(value = "X-Token", required = false) String token, @RequestBody BorrowRequest req) {
        Account me = auth.require(token, "stu");
        Book b = books.findById(req.bookId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
        if (loans.countByBookIdAndStatusIn(b.id, Loan.ACTIVE) >= b.copies)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No copies available right now.");
        if (loans.existsByBookIdAndStudentIdAndStatusIn(b.id, me.id, Loan.ACTIVE))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have this book.");
        Loan l = new Loan();
        l.bookId = b.id;
        l.bookTitle = b.title;
        l.studentId = me.id;
        l.status = "REQUESTED";
        l.days = Math.min(Math.max(req.days(), 1), 60);
        l.requestedAt = System.currentTimeMillis();
        return loans.save(l);
    }

    @PostMapping("/{id}/issue")
    public Loan issue(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long id) {
        auth.require(token, "lib");
        Loan l = find(id);
        if (!"REQUESTED".equals(l.status)) throw conflict("This request is no longer open.");
        l.status = "ISSUED";
        l.dueAt = System.currentTimeMillis() + l.days * DAY;
        return loans.save(l);
    }

    /** Librarian declines, or the student cancels their own request. */
    @PostMapping("/{id}/decline")
    public Loan decline(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long id) {
        Account me = auth.require(token);
        Loan l = find(id);
        boolean owner = "stu".equals(me.role) && me.id.equals(l.studentId);
        if (!"lib".equals(me.role) && !owner) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed.");
        if (!"REQUESTED".equals(l.status)) throw conflict("This request is no longer open.");
        l.status = "DECLINED";
        return loans.save(l);
    }

    /** Student says "I returned it"; the librarian still has to confirm. */
    @PostMapping("/{id}/return-request")
    public Loan returnRequest(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long id) {
        Account me = auth.require(token, "stu");
        Loan l = find(id);
        if (!me.id.equals(l.studentId) || !"ISSUED".equals(l.status)) throw conflict("Cannot request a return for this book.");
        l.returnRequested = true;
        return loans.save(l);
    }

    @PostMapping("/{id}/return")
    public Loan confirmReturn(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long id) {
        auth.require(token, "lib");
        Loan l = find(id);
        if (!"ISSUED".equals(l.status)) throw conflict("This book is not issued.");
        l.status = "RETURNED";
        l.returnedAt = System.currentTimeMillis();
        l.returnRequested = false;
        return loans.save(l);
    }

    @PostMapping("/{id}/extend")
    public Loan extend(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long id) {
        auth.require(token, "lib");
        Loan l = find(id);
        if (!"ISSUED".equals(l.status) || l.dueAt == null) throw conflict("This book is not issued.");
        l.dueAt += 7 * DAY;
        return loans.save(l);
    }

    private Loan find(Long id) {
        return loans.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found."));
    }

    private ResponseStatusException conflict(String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, msg);
    }
}
