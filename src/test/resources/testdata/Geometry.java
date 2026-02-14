package io.github.cyfko.example;

import jakarta.persistence.*;

@Entity
@Table(name = "gemotries")
public class Geometry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
