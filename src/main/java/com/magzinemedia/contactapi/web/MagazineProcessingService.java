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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MagazineProcessingService {

    private static final Logger log = LoggerFactory.getLogger(MagazineProcessingService.class);
    private static final float DPI = 200f;
    private static final float JPEG_QUALITY = 0.9f;

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

        try {
            byte[] pdfBytes = r2StorageService.download(magazine.getPdfUrl());
            List<String> pageImageUrls = renderPagesToR2(magazineId, pdfBytes);

            magazine.setPageImageUrls(pageImageUrls);
            magazine.setStatus(Magazine.Status.READY);
            magazineRepository.save(magazine);
        } catch (Exception e) {
            log.warn("Failed to process magazine {}: {}", magazineId, e.getMessage());
            magazine.setStatus(Magazine.Status.FAILED);
            magazineRepository.save(magazine);
        }
    }

    private List<String> renderPagesToR2(Long magazineId, byte[] pdfBytes) throws Exception {
        List<String> urls = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, DPI, ImageType.RGB);
                byte[] jpegBytes = toJpeg(image);
                image.flush();

                String key = "pages/" + magazineId + "/" + (i + 1) + ".jpg";
                urls.add(r2StorageService.uploadBytes(key, jpegBytes, "image/jpeg"));
            }
        }

        return urls;
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
