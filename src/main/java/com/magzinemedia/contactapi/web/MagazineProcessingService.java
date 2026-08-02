package com.magzinemedia.contactapi.web;

import com.magzinemedia.contactapi.model.Magazine;
import com.magzinemedia.contactapi.repository.MagazineRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class MagazineProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MagazineProcessingService.class);
    // Trimmed down from 200 — Render's free tier has very little heap, and
    // rendering happens one page at a time anyway so this is a straight
    // safety margin against the OutOfMemoryError observed at 200 DPI.
    private static final float DPI = 150f;
    private static final float JPEG_QUALITY = 0.85f;
    // Bounds a single page's render time. Client-side pdf.js was observed
    // (this session, live testing) to hang indefinitely on certain page
    // content in a real magazine PDF — PDFBox could plausibly hit the same
    // pathological content. Without a bound, one bad page leaves the
    // magazine stuck in PROCESSING forever instead of failing visibly.
    private static final long PAGE_RENDER_TIMEOUT_SECONDS = 45;

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

        Path tempFile = null;
        try {
            // Build the path without creating the file — ResponseTransformer.toFile()
            // in this SDK version always opens with CREATE_NEW and throws if the
            // target already exists, so Files.createTempFile() (which creates it
            // up front) made every download fail immediately.
            tempFile = Files.createTempDirectory("magazine-" + magazineId + "-")
                .resolve("source.pdf");
            // Stream to disk rather than loading into a byte[] — the source
            // PDFs here run 30-40MB and buffering the whole thing in heap was
            // enough to throw OutOfMemoryError on Render's free-tier memory budget.
            r2StorageService.downloadToFile(magazine.getPdfUrl(), tempFile);

            List<String> pageImageUrls = renderPagesToR2(magazineId, tempFile.toFile());

            magazine.setPageImageUrls(pageImageUrls);
            magazine.setStatus(Magazine.Status.READY);
            magazineRepository.save(magazine);
            log.info("Magazine {} processed successfully: {} pages", magazineId, pageImageUrls.size());
        } catch (Throwable t) {
            // Catches Throwable, not just Exception — OutOfMemoryError is an
            // Error, not an Exception, and previously slipped past this catch
            // block entirely, leaving the row stuck in PROCESSING forever.
            log.warn("Failed to process magazine {}: {}", magazineId, t.getMessage());
            magazine.setStatus(Magazine.Status.FAILED);
            magazineRepository.save(magazine);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    Files.deleteIfExists(tempFile.getParent());
                } catch (Exception e) {
                    log.warn("Failed to delete temp file {}: {}", tempFile, e.getMessage());
                }
            }
        }
    }

    private List<String> renderPagesToR2(Long magazineId, File pdfFile) throws Exception {
        List<String> urls = new ArrayList<>();
        ExecutorService renderExecutor = Executors.newSingleThreadExecutor();

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            log.info("Magazine {}: starting render of {} pages", magazineId, pageCount);

            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderPageWithTimeout(renderExecutor, renderer, i, magazineId);
                byte[] jpegBytes = toJpeg(image);
                image.flush();

                String key = "pages/" + magazineId + "/" + (i + 1) + ".jpg";
                urls.add(r2StorageService.uploadBytes(key, jpegBytes, "image/jpeg"));
                log.info("Magazine {}: rendered page {}/{}", magazineId, i + 1, pageCount);

                // Render's free tier gives the JVM very little heap. A page's
                // BufferedImage can be tens of MB and the next page's render
                // starts immediately — nudge the collector so it's reclaimed
                // before that allocation rather than relying on GC to keep up.
                System.gc();
            }
        } finally {
            renderExecutor.shutdownNow();
        }

        return urls;
    }

    private BufferedImage renderPageWithTimeout(
        ExecutorService executor,
        PDFRenderer renderer,
        int pageIndex,
        Long magazineId
    ) throws Exception {
        Callable<BufferedImage> renderTask = () -> renderer.renderImageWithDPI(pageIndex, DPI, ImageType.RGB);
        Future<BufferedImage> future = executor.submit(renderTask);

        try {
            return future.get(PAGE_RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new TimeoutException(
                "Magazine " + magazineId + " page " + (pageIndex + 1) + " took longer than "
                    + PAGE_RENDER_TIMEOUT_SECONDS + "s to render — aborting"
            );
        }
    }

    private byte[] toJpeg(BufferedImage image) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }
}
