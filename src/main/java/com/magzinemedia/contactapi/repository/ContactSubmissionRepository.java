package com.magzinemedia.contactapi.repository;

import com.magzinemedia.contactapi.model.ContactSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactSubmissionRepository extends JpaRepository<ContactSubmission, Long> {
}
