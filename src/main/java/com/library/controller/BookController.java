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
 * Books.
 *  - Students see the books of ALL libraries.
 *  - A librarian sees, adds, edits and deletes only HIS OWN books.
 */
@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookRepository books;
    private final LoanRepository loans;
    private final AccountRepository accounts;
    private final AuthService auth;

    public BookController(BookRepository books, LoanRepository loans, AccountRepository accounts, AuthService auth) {
        this.books = books;
        this.loans = loans;
        this.accounts = accounts;
        this.auth = auth;
    }

    @GetMapping
    public List<Book> all(@RequestHeader(value = "X-Token", required = false) String token) {
        Account me = auth.require(token);
        List<Book> list = books.findAll(Sort.by("title"));
        if ("lib".equals(me.role)) {
            list = list.stream().filter(b -> me.id.equals(b.ownerId)).collect(Collectors.toList());
        }

        Map<Long, Integer> active = new HashMap<>(), times = new HashMap<>();
        for (Loan l : loans.findAll()) {
            if (Loan.ACTIVE.contains(l.status)) active.merge(l.bookId, 1, Integer::sum);
            if ("ISSUED".equals(l.status) || "RETURNED".equals(l.status)) times.merge(l.bookId, 1, Integer::sum);
        }
        Set<String> ownerIds = list.stream().map(b -> b.ownerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Account> owners = new HashMap<>();
        accounts.findAllById(ownerIds).forEach(a -> owners.put(a.id, a));

        for (Book b : list) {
            b.available = b.copies - active.getOrDefault(b.id, 0);
            b.timesBorrowed = times.getOrDefault(b.id, 0);
            Account o = b.ownerId == null ? null : owners.get(b.ownerId);
            b.libraryName = (o != null && notBlank(o.libraryName)) ? o.libraryName : "Library";
            b.libraryAddress = notBlank(b.address) ? b.address : (o != null ? o.address : null);
        }
        return list;
    }

    @PostMapping
    public Book add(@RequestHeader(value = "X-Token", required = false) String token, @RequestBody Book in) {
        Account me = auth.require(token, "lib");
        validate(in);
        Book b = new Book();
        copyFields(in, b, me);
        b.ownerId = me.id;                                  // the creating librarian owns the book
        b.createdAt = System.currentTimeMillis();
        return books.save(b);
    }

    @PutMapping("/{id}")
    public Book update(@RequestHeader(value = "X-Token", required = false) String token,
                       @PathVariable Long id, @RequestBody Book in) {
        Account me = auth.require(token, "lib");
        validate(in);
        Book b = ownBook(me, id);
        long active = loans.countByBookIdAndStatusIn(id, Loan.ACTIVE);
        copyFields(in, b, me);
        b.copies = (int) Math.max(in.copies, active);       // never fewer copies than are on loan
        return books.save(b);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long id) {
        Account me = auth.require(token, "lib");
        ownBook(me, id);
        if (loans.countByBookIdAndStatusIn(id, Loan.ACTIVE) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This book is on loan or requested.");
        books.deleteById(id);
        return Map.of("ok", true);
    }

    /** Finds the book and answers 403 if it belongs to another librarian. */
    private Book ownBook(Account me, Long id) {
        Book b = books.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
        if (!me.id.equals(b.ownerId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only change your own books.");
        return b;
    }

    /** Copies only the fields a librarian may set. The owner is never taken from the request. */
    private void copyFields(Book in, Book b, Account me) {
        b.title = in.title.trim();
        b.author = in.author.trim();
        b.genre = in.genre;
        b.description = in.description;
        b.cover = in.cover;
        b.copies = in.copies;
        b.address = notBlank(in.address) ? in.address.trim() : me.address;   // default: the librarian's profile address
    }

    private void validate(Book b) {
        if (b.title == null || b.title.isBlank() || b.author == null || b.author.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title and author are required.");
        if (b.copies < 1) b.copies = 1;
        if (b.copies > 999) b.copies = 999;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
