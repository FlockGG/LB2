package com.library.service;

import com.library.model.Account;
import com.library.model.Book;
import com.library.model.Loan;
import com.library.repository.AccountRepository;
import com.library.repository.BookRepository;
import com.library.repository.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Runs on every start-up. Books (and loans) that have no owner yet, for example data created before
 * libraries were separated, are given to the OLDEST librarian account.
 * Harmless to repeat: once everything has an owner it changes nothing.
 */
@Component
public class OwnershipMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OwnershipMigration.class);
    private final AccountRepository accounts;
    private final BookRepository books;
    private final LoanRepository loans;

    public OwnershipMigration(AccountRepository accounts, BookRepository books, LoanRepository loans) {
        this.accounts = accounts;
        this.books = books;
        this.loans = loans;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Account> librarians = accounts.findByRole("lib");
            if (librarians.isEmpty()) return;               // nobody to assign to yet; runs again next start
            Account oldest = librarians.stream()
                    .min(Comparator.comparingLong((Account a) -> a.createdAt).thenComparing(a -> a.id))
                    .get();

            int movedBooks = 0, movedLoans = 0;
            for (Book b : books.findAll()) {
                if (b.ownerId != null) continue;
                b.ownerId = oldest.id;
                if ((b.address == null || b.address.isBlank()) && oldest.address != null) b.address = oldest.address;
                books.save(b);
                movedBooks++;
            }
            for (Loan l : loans.findAll()) {
                if (l.ownerId != null) continue;
                String owner = books.findById(l.bookId).map(b -> b.ownerId).orElse(null);
                l.ownerId = owner != null ? owner : oldest.id;
                loans.save(l);
                movedLoans++;
            }
            if (movedBooks + movedLoans > 0)
                log.info("Ownership migration: gave {} books and {} loans to the oldest librarian ({}).", movedBooks, movedLoans, oldest.id);
        } catch (Exception e) {
            log.error("Ownership migration failed (the app keeps running): {}", e.toString());
        }
    }
}
