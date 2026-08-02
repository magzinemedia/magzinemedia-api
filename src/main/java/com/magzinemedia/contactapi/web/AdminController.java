package com.magzinemedia.contactapi.web;

import com.magzinemedia.contactapi.model.ContactSubmission;
import com.magzinemedia.contactapi.model.Magazine;
import com.magzinemedia.contactapi.repository.ContactSubmissionRepository;
import com.magzinemedia.contactapi.repository.MagazineRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public Map<String, Object> presignMagazineUpload(@Valid @RequestBody MagazinePresignRequest request) {
        String coverKey = r2StorageService.buildKey("covers", request.getCoverFileName());
        String pdfKey = r2StorageService.buildKey("pdfs", request.getPdfFileName());

        R2StorageService.PresignedUpload cover = r2StorageService.presignUpload(coverKey, request.getCoverContentType());
        R2StorageService.PresignedUpload pdf = r2StorageService.presignUpload(pdfKey, request.getPdfContentType());

        return Map.of(
            "coverUploadUrl", cover.uploadUrl(),
            "coverPublicUrl", cover.publicUrl(),
            "pdfUploadUrl", pdf.uploadUrl(),
            "pdfPublicUrl", pdf.publicUrl()
        );
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
}
