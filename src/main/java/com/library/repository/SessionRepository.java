package com.library.repository;

import com.library.model.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<UserSession, String> {}
