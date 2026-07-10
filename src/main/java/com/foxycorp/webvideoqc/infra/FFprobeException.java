package com.foxycorp.webvideoqc.infra;

public class FFprobeException extends RuntimeException {
    public FFprobeException(String msg) {
        super(msg);
    }

    public FFprobeException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
