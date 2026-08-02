package com.magzinemedia.contactapi.web;

import jakarta.validation.constraints.NotBlank;

public class MagazinePresignRequest {

    @NotBlank
    private String coverFileName;

    @NotBlank
    private String coverContentType;

    @NotBlank
    private String pdfFileName;

    @NotBlank
    private String pdfContentType;

    public String getCoverFileName() {
        return coverFileName;
    }

    public void setCoverFileName(String coverFileName) {
        this.coverFileName = coverFileName;
    }

    public String getCoverContentType() {
        return coverContentType;
    }

    public void setCoverContentType(String coverContentType) {
        this.coverContentType = coverContentType;
    }

    public String getPdfFileName() {
        return pdfFileName;
    }

    public void setPdfFileName(String pdfFileName) {
        this.pdfFileName = pdfFileName;
    }

    public String getPdfContentType() {
        return pdfContentType;
    }

    public void setPdfContentType(String pdfContentType) {
        this.pdfContentType = pdfContentType;
    }
}
