package com.magzinemedia.contactapi.web;

import com.magzinemedia.contactapi.model.Magazine;
import com.magzinemedia.contactapi.repository.MagazineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class MagazineProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MagazineProcessingService.class);
    // Rendering via PDFBox (in-JVM) was hitting OutOfMemoryError on Render's
    // free tier — each page's BufferedImage + Java2D rendering pipeline was
    // too much for the available heap. pdftoppm (poppler-utils) renders each
    // page in a native subprocess that releases all its memory back to the OS
    // the instant it exits, sidestepping JVM heap/GC pressure entirely.
    private static final int DPI = 150;
    private static final int JPEG_QUALITY = 85;
    private static final long PAGE_RENDER_TIMEOUT_SECONDS = 45;
    private static final Pattern PAGES_PATTERN = Pattern.compile("(?m)^Pages:\\s*(\\d+)\\s*$");

    private final MagazineRepository magazineRepository;
    private final R2StorageService r2StorageService;

    public MagazineProcessingService(MagazineRepository magazineRepository, R2StorageService r2StorageService) {
        this.magazineRepository = magazineRepository;
        this.r2StorageService = r2StorageService;
    }

    @Async
    public void processMagazine(Long magazineId) {
        Optional<Magazine> maybeMagazine = magazineRepository.findById(magazineId);
        if (maybeMagazine.isEmpty()) {
            return;
        }
        Magazine magazine = maybeMagazine.get();

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("magazine-" + magazineId + "-");
            Path pdfFile = workDir.resolve("source.pdf");
            // Build the path without creating the file — ResponseTransformer.toFile()
            // always opens with CREATE_NEW and throws if the target already exists.
            r2StorageService.downloadToFile(magazine.getPdfUrl(), pdfFile);

            List<String> pageImageUrls = renderPagesToR2(magazineId, pdfFile, workDir);

            magazine.setPageImageUrls(pageImageUrls);
            magazine.setStatus(Magazine.Status.READY);
            magazineRepository.save(magazine);
            log.info("Magazine {} processed successfully: {} pages", magazineId, pageImageUrls.size());
        } catch (Throwable t) {
            // Catches Throwable, not just Exception — OutOfMemoryError is an
            // Error, not an Exception, and previously slipped past a narrower
            // catch block entirely, leaving the row stuck in PROCESSING forever.
            log.warn("Failed to process magazine {}: {}", magazineId, t.getMessage());
            magazine.setStatus(Magazine.Status.FAILED);
            magazineRepository.save(magazine);
        } finally {
            deleteRecursively(workDir);
        }
    }

    private List<String> renderPagesToR2(Long magazineId, Path pdfFile, Path workDir) throws Exception {
        int pageCount = readPageCount(pdfFile);
        log.info("Magazine {}: starting render of {} pages", magazineId, pageCount);

        List<String> urls = new ArrayList<>();
        for (int page = 1; page <= pageCount; page++) {
            byte[] jpegBytes = renderPageWithTimeout(pdfFile, workDir, page, magazineId);

            String key = "pages/" + magazineId + "/" + page + ".jpg";
            urls.add(r2StorageService.uploadBytes(key, jpegBytes, "image/jpeg"));
            log.info("Magazine {}: rendered page {}/{}", magazineId, page, pageCount);
        }

        return urls;
    }

    private int readPageCount(Path pdfFile) throws Exception {
        Process process = new ProcessBuilder("pdfinfo", pdfFile.toString())
            .redirectErrorStream(true)
            .start();

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("pdfinfo timed out reading " + pdfFile.getFileName());
        }
        String output = readAll(process);
        if (process.exitValue() != 0) {
            throw new RuntimeException("pdfinfo failed (exit " + process.exitValue() + "): " + output);
        }

        Matcher matcher = PAGES_PATTERN.matcher(output);
        if (!matcher.find()) {
            throw new RuntimeException("Could not determine page count from pdfinfo output: " + output);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private byte[] renderPageWithTimeout(Path pdfFile, Path workDir, int page, Long magazineId) throws Exception {
        Path pageDir = Files.createTempDirectory(workDir, "page-" + page + "-");
        try {
            Process process = new ProcessBuilder(
                "pdftoppm",
                "-jpeg",
                "-jpegopt", "quality=" + JPEG_QUALITY,
                "-r", String.valueOf(DPI),
                "-f", String.valueOf(page),
                "-l", String.valueOf(page),
                pdfFile.toString(),
                pageDir.resolve("out").toString()
            ).redirectErrorStream(true).start();

            boolean finished = process.waitFor(PAGE_RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException(
                    "Magazine " + magazineId + " page " + page + " took longer than "
                        + PAGE_RENDER_TIMEOUT_SECONDS + "s to render — aborting"
                );
            }
            String output = readAll(process);
            if (process.exitValue() != 0) {
                throw new RuntimeException("pdftoppm failed on page " + page + " (exit " + process.exitValue() + "): " + output);
            }

            try (Stream<Path> files = Files.list(pageDir)) {
                Path rendered = files.findFirst()
                    .orElseThrow(() -> new RuntimeException("pdftoppm produced no output for page " + page));
                return Files.readAllBytes(rendered);
            }
        } finally {
            deleteRecursively(pageDir);
        }
    }

    private String readAll(Process process) throws Exception {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception e) {
                        log.warn("Failed to delete {}: {}", p, e.getMessage());
                    }
                });
        } catch (Exception e) {
            log.warn("Failed to clean up temp directory {}: {}", dir, e.getMessage());
        }
    }
}
