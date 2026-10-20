package com.library.repository;

import com.library.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface LoanRepository extends JpaRepository<Loan, Long> {
    List<Loan> findByStudentId(String studentId);
    long countByBookIdAndStatusIn(Long bookId, Collection<String> statuses);
    boolean existsByBookIdAndStudentIdAndStatusIn(Long bookId, String studentId, Collection<String> statuses);
}
