package com.library.model;

import jakarta.persistence.*;

/** An uploaded picture (book cover or profile photo), kept inside the database so it is never lost. */
@Entity
@Table(name = "images")
public class StoredImage {
    @Id
    public String id;
    public String contentType;
    @Column(length = 6_000_000)
    public byte[] data;
    public long createdAt;
}
