package com.foxycorp.webvideoqc.controller;

import com.foxycorp.webvideoqc.infra.FFmpegException;
import com.foxycorp.webvideoqc.infra.FFprobeException;
import com.foxycorp.webvideoqc.model.ErrorResponse;
import com.foxycorp.webvideoqc.service.VideoFileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> badRequestValidation(MethodArgumentNotValidException ex) {
        String message = "Invalid request";
        if (ex.getBindingResult().getFieldError() != null && ex.getBindingResult().getFieldError().getDefaultMessage() != null) {
            message = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badRequest(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_PATH", ex.getMessage());
    }

    @ExceptionHandler(VideoFileNotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(VideoFileNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "VIDEO_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(FFprobeException.class)
    public ResponseEntity<ErrorResponse> ffprobeFailure(FFprobeException ex) {
        String message = ex.getMessage() == null ? "Failed to read video metadata" : ex.getMessage();
        if (message.contains("No video stream found")) {
            return build(HttpStatus.INTERNAL_SERVER_ERROR, "UNPROCESSABLE_VIDEO", message);
        }
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "FFPROBE_ERROR", message);
    }

    @ExceptionHandler(FFmpegException.class)
    public ResponseEntity<ErrorResponse> ffmpegFailure(FFmpegException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "FFMPEG_ERROR", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internal(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error");
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

}
