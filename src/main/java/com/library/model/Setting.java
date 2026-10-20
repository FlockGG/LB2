package com.library.model;

import jakarta.persistence.*;

/** Small key/value table. Used to store the hashed librarian staff key. */
@Entity
@Table(name = "settings")
public class Setting {
    @Id
    public String name;
    public String val;

    public Setting() {}
    public Setting(String name, String val) { this.name = name; this.val = val; }
}
