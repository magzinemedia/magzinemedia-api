package com.magzinemedia.contactapi.web;

import jakarta.validation.constraints.NotBlank;

public class MagazineCreateRequest {

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String coverImageUrl;

    @NotBlank
    private String pdfUrl;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }
}
