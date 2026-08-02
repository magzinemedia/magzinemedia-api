package com.magzinemedia.contactapi.repository;

import com.magzinemedia.contactapi.model.Magazine;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MagazineRepository extends JpaRepository<Magazine, Long> {
}
