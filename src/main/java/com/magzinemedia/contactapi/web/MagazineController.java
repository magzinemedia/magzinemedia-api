package com.magzinemedia.contactapi.web;

import com.magzinemedia.contactapi.model.Magazine;
import com.magzinemedia.contactapi.repository.MagazineRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/magazines")
public class MagazineController {

    private final MagazineRepository repository;

    public MagazineController(MagazineRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Magazine> list() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @GetMapping("/{id}")
    public Magazine get(@PathVariable Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Magazine not found"));
    }
}
