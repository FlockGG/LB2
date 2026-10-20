package com.library.controller;

import com.library.model.Account;
import com.library.repository.AccountRepository;
import com.library.service.AuthService;
import com.library.service.StatsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Profiles, saved books and the statistics cards. */
@RestController
@RequestMapping("/api")
public class ProfileController {
    private final AccountRepository accounts;
    private final AuthService auth;
    private final StatsService stats;

    public ProfileController(AccountRepository accounts, AuthService auth, StatsService stats) {
        this.accounts = accounts;
        this.auth = auth;
        this.stats = stats;
    }

    @GetMapping("/me")
    public Account me(@RequestHeader(value = "X-Token", required = false) String token) {
        return auth.require(token);
    }

    /** Student: own reading record. Librarian: library numbers. */
    @GetMapping("/me/stats")
    public Map<String, Object> myStats(@RequestHeader(value = "X-Token", required = false) String token) {
        Account me = auth.require(token);
        return "lib".equals(me.role) ? stats.library(me.id) : stats.student(me.id, null);
    }

    /** Update my own profile. Only the fields that belong to my role are changed. */
    @PutMapping("/profile")
    public Account update(@RequestHeader(value = "X-Token", required = false) String token, @RequestBody Map<String, String> b) {
        Account me = auth.require(token);
        if (b.containsKey("about")) me.about = clean(b.get("about"), 2000);
        if (b.containsKey("photo")) me.photo = clean(b.get("photo"), 255);
        if ("lib".equals(me.role)) {
            if (b.containsKey("libraryName")) me.libraryName = clean(b.get("libraryName"), 120);
            if (b.containsKey("address")) me.address = clean(b.get("address"), 255);
            if (b.containsKey("hours")) me.hours = clean(b.get("hours"), 255);
        } else {
            if (b.containsKey("name")) me.name = clean(b.get("name"), 120);
            if (b.containsKey("school")) me.school = clean(b.get("school"), 120);
            if (b.containsKey("genres")) me.genres = clean(b.get("genres"), 255);
        }
        return accounts.save(me);
    }

    /** Student: save / un-save a book. */
    @PostMapping("/saved/{bookId}")
    public Account toggleSaved(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable Long bookId) {
        Account me = auth.require(token, "stu");
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : (me.saved == null ? "" : me.saved).split(",")) if (!s.isBlank()) set.add(s);
        String id = String.valueOf(bookId);
        if (!set.remove(id)) set.add(id);
        me.saved = String.join(",", set);
        return accounts.save(me);
    }

    /**
     * Profile and numbers of ONE library. Students pass the ownerId of the book they are looking at.
     * A librarian without ownerId gets his own library.
     */
    @GetMapping("/library")
    public Map<String, Object> library(@RequestHeader(value = "X-Token", required = false) String token,
                                       @RequestParam(value = "ownerId", required = false) String ownerId) {
        Account me = auth.require(token);
        if (ownerId == null || ownerId.isBlank()) {
            if ("lib".equals(me.role)) ownerId = me.id;
            else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Open a book first to see its library.");
        }
        Account lib = accounts.findById(ownerId).filter(a -> "lib".equals(a.role)).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("account", lib);
        m.put("stats", lib == null ? null : stats.library(ownerId));
        return m;
    }

    /** Librarian: profile and behaviour record of a student who has borrowed from MY library. */
    @GetMapping("/students/{id}")
    public Map<String, Object> student(@RequestHeader(value = "X-Token", required = false) String token, @PathVariable String id) {
        Account me = auth.require(token, "lib");
        Account a = accounts.findById(id).filter(x -> "stu".equals(x.role))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found."));
        if (!stats.hasLoanWith(id, me.id))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This student has not used your library.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("account", a);
        m.put("stats", stats.student(id, me.id));
        return m;
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String clean(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() > max ? s.substring(0, max) : s;
    }
}
