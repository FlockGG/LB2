package com.library.repository;

import com.library.model.StoredImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<StoredImage, String> {}
