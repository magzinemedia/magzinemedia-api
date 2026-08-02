package com.magzinemedia.contactapi.web;

import com.magzinemedia.contactapi.model.ContactSubmission;
import com.magzinemedia.contactapi.model.Magazine;
import com.magzinemedia.contactapi.repository.ContactSubmissionRepository;
import com.magzinemedia.contactapi.repository.MagazineRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ContactSubmissionRepository repository;
    private final MagazineRepository magazineRepository;
    private final R2StorageService r2StorageService;

    public AdminController(
        ContactSubmissionRepository repository,
        MagazineRepository magazineRepository,
        R2StorageService r2StorageService
    ) {
        this.repository = repository;
        this.magazineRepository = magazineRepository;
        this.r2StorageService = r2StorageService;
    }

    @GetMapping("/contacts")
    public List<ContactSubmission> listContacts() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @PostMapping("/magazines/presign")
    public Map<String, Object> presignMagazineUpload(@RequestBody MagazinePresignRequest request) {
        Map<String, Object> response = new HashMap<>();

        if (request.getCoverFileName() != null && !request.getCoverFileName().isBlank()) {
            String coverKey = r2StorageService.buildKey("covers", request.getCoverFileName());
            R2StorageService.PresignedUpload cover = r2StorageService.presignUpload(coverKey, request.getCoverContentType());
            response.put("coverUploadUrl", cover.uploadUrl());
            response.put("coverPublicUrl", cover.publicUrl());
        }

        if (request.getPdfFileName() != null && !request.getPdfFileName().isBlank()) {
            String pdfKey = r2StorageService.buildKey("pdfs", request.getPdfFileName());
            R2StorageService.PresignedUpload pdf = r2StorageService.presignUpload(pdfKey, request.getPdfContentType());
            response.put("pdfUploadUrl", pdf.uploadUrl());
            response.put("pdfPublicUrl", pdf.publicUrl());
        }

        return response;
    }

    @PostMapping("/magazines")
    public Magazine createMagazine(@Valid @RequestBody MagazineCreateRequest request) {
        Magazine magazine = new Magazine();
        magazine.setTitle(request.getTitle());
        magazine.setDescription(request.getDescription());
        magazine.setCoverImageUrl(request.getCoverImageUrl());
        magazine.setPdfUrl(request.getPdfUrl());
        return magazineRepository.save(magazine);
    }

    @PutMapping("/magazines/{id}")
    public Magazine updateMagazine(@PathVariable Long id, @Valid @RequestBody MagazineCreateRequest request) {
        Magazine magazine = magazineRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Magazine not found"));

        magazine.setTitle(request.getTitle());
        magazine.setDescription(request.getDescription());
        magazine.setCoverImageUrl(request.getCoverImageUrl());
        magazine.setPdfUrl(request.getPdfUrl());
        return magazineRepository.save(magazine);
    }

    @DeleteMapping("/magazines/{id}")
    public ResponseEntity<Void> deleteMagazine(@PathVariable Long id) {
        Magazine magazine = magazineRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Magazine not found"));

        r2StorageService.deleteByPublicUrl(magazine.getCoverImageUrl());
        r2StorageService.deleteByPublicUrl(magazine.getPdfUrl());
        magazineRepository.delete(magazine);

        return ResponseEntity.noContent().build();
    }
}
