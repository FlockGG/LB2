package com.library.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

/** One user account. role is "lib" (librarian) or "stu" (student). */
@Entity
@Table(name = "accounts")
public class Account {
    @Id
    public String id;
    public String role;
    @JsonIgnore
    public String codeHash;          // SHA-256 of the secret code (the code itself is never stored)
    public String name;              // student name
    public String school;            // student class / roll number
    public String libraryName;       // librarian only
    public String address;           // librarian only
    public String hours;             // librarian only (desk hours)
    public String genres;            // student favourite genres
    @Column(length = 2000)
    public String about;
    public String photo;
    @Column(length = 4000)
    public String saved = "";        // student's saved book ids, e.g. "3,7,12"
    public long createdAt;
}
