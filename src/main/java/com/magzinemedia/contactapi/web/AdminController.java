package com.magzinemedia.contactapi.web;

import com.magzinemedia.contactapi.model.ContactSubmission;
import com.magzinemedia.contactapi.repository.ContactSubmissionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ContactSubmissionRepository repository;

    public AdminController(ContactSubmissionRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/contacts")
    public List<ContactSubmission> listContacts() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
