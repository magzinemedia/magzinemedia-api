package com.magzinemedia.contactapi.web;

import com.magzinemedia.contactapi.model.ContactSubmission;
import com.magzinemedia.contactapi.repository.ContactSubmissionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ContactController {

    private final ContactSubmissionRepository repository;
    private final EmailNotificationService emailNotificationService;

    public ContactController(ContactSubmissionRepository repository, EmailNotificationService emailNotificationService) {
        this.repository = repository;
        this.emailNotificationService = emailNotificationService;
    }

    @PostMapping("/api/contact")
    public ResponseEntity<Map<String, Object>> submit(@Valid @RequestBody ContactRequest request) {
        ContactSubmission submission = new ContactSubmission();
        submission.setName(request.getName());
        submission.setEmail(request.getEmail());
        submission.setMobile(request.getMobile());
        submission.setCountryCode(request.getCountryCode());
        submission.setFullPhone(request.getFullPhone());
        submission.setCompany(request.getCompany());
        submission.setNotes(request.getNotes());

        ContactSubmission saved = repository.save(submission);
        emailNotificationService.notifyNewContact(saved);
        emailNotificationService.sendCustomerAcknowledgement(saved);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("ok", true, "id", saved.getId()));
    }
}
