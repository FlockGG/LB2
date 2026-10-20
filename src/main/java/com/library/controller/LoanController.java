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

/**
 * Borrowing workflow: request -> issue -> return request -> confirm return.
 * A request goes only to the librarian who owns the book, and only that librarian can handle it.
 */
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

    /** Librarian: only the loans of MY books, with the student's name attached. */
    @GetMapping
    public List<Loan> all(@RequestHeader(value = "X-Token", required = false) String token) {
        Account me = auth.require(token, "lib");
        Map<Long, String> bookOwners = new HashMap<>();
        for (Book b : books.findAll()) if (b.ownerId != null) bookOwners.put(b.id, b.ownerId);

        List<Loan> list = loans.findAll(Sort.by(Sort.Direction.DESC, "id")).stream()
                .filter(l -> me.id.equals(l.ownerId != null ? l.ownerId : bookOwners.get(l.bookId)))
                .collect(Collectors.toList());

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

    /** Student: only my own loans (with the name of the library that owns each book). */
    @GetMapping("/mine")
    public List<Loan> mine(@RequestHeader(value = "X-Token", required = false) String token) {
        Account me = auth.require(token, "stu");
        List<Loan> list = loans.findByStudentId(me.id);
        list.sort((a, b) -> Long.compare(b.requestedAt, a.requestedAt));
        Map<String, Account> owners = new HashMap<>();
        accounts.findAllById(list.stream().map(this::ownerOf).filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(a -> owners.put(a.id, a));
        for (Loan l : list) {
            Account o = owners.get(ownerOf(l));
            l.libraryName = (o != null && o.libraryName != null && !o.libraryName.isBlank()) ? o.libraryName : "Library";
        }
        return list;
    }

    @PostMapping
    public Loan request(@RequestHeader(value = "X-Token", required = false) String token, @RequestBody BorrowRequest req) {
        Account me = auth.require(token, "stu");
        Book b = books.findById(req.bookId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
        if (b.ownerId == null) throw conflict("This book does not belong to a library yet.");
        if (loans.countByBookIdAndStatusIn(b.id, Loan.ACTIVE) >= b.copies)
            throw conflict("No copies available right now.");
        if (loans.existsByBookIdAndStudentIdAndStatusIn(b.id, me.id, Loan.ACTIVE))
            throw conflict("You already have this book.");
        Loan l = new Loan();
        l.bookId = b.id;
        l.bookTitle = b.title;
        l.studentId = me.id;
        l.ownerId = b.ownerId;                               // the request goes only to the book's owner
        l.status = "REQUESTED";
        l.days = Math.min(Math.max(req.days(), 1), 60);
        l.requestedAt = System.currentTimeMillis();
        return loans.save(l);
    }

    @PostMapping("/{id}/issue")
    public Loan issue(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long id) {
        Account me = auth.require(token, "lib");
        Loan l = ownLoan(me, id);
        if (!"REQUESTED".equals(l.status)) throw conflict("This request is no longer open.");
        l.status = "ISSUED";
        l.dueAt = System.currentTimeMillis() + l.days * DAY;
        return loans.save(l);
    }

    /** The owning librarian declines, or the student cancels their own request. */
    @PostMapping("/{id}/decline")
    public Loan decline(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long id) {
        Account me = auth.require(token);
        Loan l = find(id);
        boolean student = "stu".equals(me.role) && me.id.equals(l.studentId);
        boolean owner = "lib".equals(me.role) && me.id.equals(ownerOf(l));
        if (!student && !owner) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed.");
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
        Account me = auth.require(token, "lib");
        Loan l = ownLoan(me, id);
        if (!"ISSUED".equals(l.status)) throw conflict("This book is not issued.");
        l.status = "RETURNED";
        l.returnedAt = System.currentTimeMillis();
        l.returnRequested = false;
        return loans.save(l);
    }

    @PostMapping("/{id}/extend")
    public Loan extend(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long id) {
        Account me = auth.require(token, "lib");
        Loan l = ownLoan(me, id);
        if (!"ISSUED".equals(l.status) || l.dueAt == null) throw conflict("This book is not issued.");
        l.dueAt += 7 * DAY;
        return loans.save(l);
    }

    // ---- helpers ----
    private Loan find(Long id) {
        return loans.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Loan not found."));
    }

    /** The librarian who owns this loan's book (stored on the loan; old loans fall back to the book). */
    private String ownerOf(Loan l) {
        if (l.ownerId != null) return l.ownerId;
        return books.findById(l.bookId).map(b -> b.ownerId).orElse(null);
    }

    /** Finds the loan and answers 403 if it belongs to another librarian. */
    private Loan ownLoan(Account me, Long id) {
        Loan l = find(id);
        if (!me.id.equals(ownerOf(l)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This request belongs to another library.");
        return l;
    }

    private ResponseStatusException conflict(String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, msg);
    }
}
