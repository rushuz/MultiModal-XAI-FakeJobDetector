package com.fakejobdetection.dto;

import org.springframework.web.multipart.MultipartFile;

public class AnalyzeRequest {

    private String text;                 // Direct job description text
    private MultipartFile image;          // Job poster / banner (OCR via Tess4J)
    private MultipartFile audio;          // Recruiter call / voice note (Vosk STT)

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public MultipartFile getImage() {
        return image;
    }

    public void setImage(MultipartFile image) {
        this.image = image;
    }

    public MultipartFile getAudio() {
        return audio;
    }

    public void setAudio(MultipartFile audio) {
        this.audio = audio;
    }
}
