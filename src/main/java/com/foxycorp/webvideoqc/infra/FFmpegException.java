package com.foxycorp.webvideoqc.infra;

public class FFmpegException extends RuntimeException {

    public FFmpegException(String message) {
        super(message);
    }

    public FFmpegException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
