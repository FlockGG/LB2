package com.library.model;

import jakarta.persistence.*;

@Entity
@Table(name = "books")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String title;
    public String author;
    public String genre;
    @Column(length = 4000)
    public String description;
    public int copies = 1;
    public String cover;             // image url, e.g. /uploads/abc.png
    public long createdAt;
    public String ownerId;           // account id of the librarian who owns this book
    public String address;           // library address shown to students for this book

    // Calculated on the fly, not stored in the database
    @Transient public int available;
    @Transient public int timesBorrowed;
    @Transient public String libraryName;      // the owner's library name
    @Transient public String libraryAddress;   // address to show (the book's address, or the owner's profile address)
}
