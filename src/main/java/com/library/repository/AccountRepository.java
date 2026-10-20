package com.library.repository;

import com.library.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByCodeHash(String codeHash);
    List<Account> findByRole(String role);
}
