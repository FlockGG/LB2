package com.library.service;

import com.library.model.Account;
import com.library.model.UserSession;
import com.library.repository.AccountRepository;
import com.library.repository.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** Login helpers: hashing, sessions and "who is calling?" checks. */
@Service
public class AuthService {
    private final SessionRepository sessions;
    private final AccountRepository accounts;

    public AuthService(SessionRepository sessions, AccountRepository accounts) {
        this.sessions = sessions;
        this.accounts = accounts;
    }

    public static String sha(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String newSession(String accountId) {
        UserSession s = new UserSession();
        s.token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        s.accountId = accountId;
        s.createdAt = System.currentTimeMillis();
        sessions.save(s);
        return s.token;
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) sessions.deleteById(token);
    }

    /** Returns the logged-in account, or answers 401 if the token is missing/invalid. */
    public Account require(String token) {
        if (token == null || token.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in.");
        UserSession s = sessions.findById(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again."));
        return accounts.findById(s.accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found."));
    }

    /** Same as require(), but also checks the role ("lib" or "stu"). Otherwise answers 403. */
    public Account require(String token, String role) {
        Account a = require(token);
        if (!role.equals(a.role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed for your account type.");
        return a;
    }
}
