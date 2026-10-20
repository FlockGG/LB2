package com.library.model;

import jakarta.persistence.*;

/** A logged-in device. The browser keeps the token and sends it with every request. */
@Entity
@Table(name = "user_sessions")
public class UserSession {
    @Id
    public String token;
    public String accountId;
    public long createdAt;
}
