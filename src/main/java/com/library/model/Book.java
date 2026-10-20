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

    // Calculated on the fly, not stored in the database
    @Transient public int available;
    @Transient public int timesBorrowed;
}
