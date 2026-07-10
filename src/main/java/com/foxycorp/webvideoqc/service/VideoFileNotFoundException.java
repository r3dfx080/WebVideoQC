package com.foxycorp.webvideoqc.service;

public class VideoFileNotFoundException extends RuntimeException {
    public VideoFileNotFoundException(String path) {
        super("Video file not found: " + path);
    }
}
