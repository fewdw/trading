package com.ufc.server.ranking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FighterRepository extends JpaRepository<Fighter, Long> {
    boolean existsByName(String name);
}
