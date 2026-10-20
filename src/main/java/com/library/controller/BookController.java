package com.library.controller;

import com.library.model.Book;
import com.library.model.Loan;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import com.library.service.AuthService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Books: everyone who is logged in can read, only librarians can change. */
@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookRepository books;
    private final LoanRepository loans;
    private final AuthService auth;

    public BookController(BookRepository books, LoanRepository loans, AuthService auth) {
        this.books = books;
        this.loans = loans;
        this.auth = auth;
    }

    @GetMapping
    public List<Book> all(@RequestHeader(value = "X-Token", required = false) String token) {
        auth.require(token);
        List<Book> list = books.findAll(Sort.by("title"));
        Map<Long, Integer> active = new HashMap<>(), times = new HashMap<>();
        for (Loan l : loans.findAll()) {
            if (Loan.ACTIVE.contains(l.status)) active.merge(l.bookId, 1, Integer::sum);
            if ("ISSUED".equals(l.status) || "RETURNED".equals(l.status)) times.merge(l.bookId, 1, Integer::sum);
        }
        for (Book b : list) {
            b.available = b.copies - active.getOrDefault(b.id, 0);
            b.timesBorrowed = times.getOrDefault(b.id, 0);
        }
        return list;
    }

    @PostMapping
    public Book add(@RequestHeader(value = "X-Token", required = false) String token, @RequestBody Book b) {
        auth.require(token, "lib");
        validate(b);
        b.id = null;
        b.createdAt = System.currentTimeMillis();
        return books.save(b);
    }

    @PutMapping("/{id}")
    public Book update(@RequestHeader(value = "X-Token", required = false) String token,
                       @PathVariable Long id, @RequestBody Book in) {
        auth.require(token, "lib");
        validate(in);
        Book b = books.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
        long active = loans.countByBookIdAndStatusIn(id, Loan.ACTIVE);
        b.title = in.title;
        b.author = in.author;
        b.genre = in.genre;
        b.description = in.description;
        b.cover = in.cover;
        b.copies = (int) Math.max(in.copies, active);   // never fewer copies than are on loan
        return books.save(b);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long id) {
        auth.require(token, "lib");
        if (loans.countByBookIdAndStatusIn(id, Loan.ACTIVE) > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This book is on loan or requested.");
        books.deleteById(id);
        return Map.of("ok", true);
    }

    private void validate(Book b) {
        if (b.title == null || b.title.isBlank() || b.author == null || b.author.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title and author are required.");
        if (b.copies < 1) b.copies = 1;
        if (b.copies > 999) b.copies = 999;
    }
}
