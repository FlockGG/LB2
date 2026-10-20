package com.library.controller;

import com.library.model.Account;
import com.library.model.Setting;
import com.library.repository.AccountRepository;
import com.library.repository.SettingRepository;
import com.library.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Create account / log in with a secret code / log out. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();
    private final AuthService auth;
    private final AccountRepository accounts;
    private final SettingRepository settings;

    public AuthController(AuthService auth, AccountRepository accounts, SettingRepository settings) {
        this.auth = auth;
        this.accounts = accounts;
        this.settings = settings;
    }

    public record CreateRequest(String role, String staffKey) {}
    public record LoginRequest(String role, String code) {}

    /** Tells the front end whether a staff key already exists (so it can ask to set or to enter it). */
    @GetMapping("/staff-key")
    public Map<String, Boolean> staffKeyExists() {
        return Map.of("exists", settings.existsById("staffKey"));
    }

    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody CreateRequest req) {
        if (!"lib".equals(req.role()) && !"stu".equals(req.role())) throw bad("Invalid account type.");
        if ("lib".equals(req.role())) checkStaffKey(req.staffKey());

        String code = newCode();
        Account a = new Account();
        a.id = UUID.randomUUID().toString();
        a.role = req.role();
        a.codeHash = AuthService.sha(code);
        a.createdAt = System.currentTimeMillis();
        accounts.save(a);
        return Map.of("code", code, "token", auth.newSession(a.id), "account", a);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        String raw = req.code() == null ? "" : req.code().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (raw.length() != 16) throw bad("Enter your full 16-character code.");
        Account a = accounts.findByCodeHash(AuthService.sha(format(raw)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Code not found. Check it and try again."));
        if (!a.role.equals(req.role()))
            throw bad("This code belongs to a " + ("lib".equals(a.role) ? "librarian" : "student") + " account.");
        return Map.of("token", auth.newSession(a.id), "account", a);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = "X-Token", required = false) String token) {
        auth.logout(token);
        return Map.of("ok", true);
    }

    // ---- helpers ----
    /** The first librarian sets the staff key; every later librarian must enter the same key. */
    private void checkStaffKey(String staffKey) {
        String key = staffKey == null ? "" : staffKey.trim();
        if (key.length() < 6) throw bad("Staff key must be at least 6 characters.");
        String hash = AuthService.sha("staff:" + key);
        Optional<Setting> saved = settings.findById("staffKey");
        if (saved.isPresent()) {
            if (!saved.get().val.equals(hash)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Wrong staff key.");
        } else {
            settings.save(new Setting("staffKey", hash));
        }
    }

    private String newCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return format(sb.toString());
    }

    private String format(String raw) {
        return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16);
    }

    private ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
