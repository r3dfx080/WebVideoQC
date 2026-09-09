package com.foxycorp.webvideoqc.model;

/**
 * A single QC finding shown beside Metadata.
 * Warnings are more severe than cautions.
 */
public record QcIssue(Severity severity, String message) {

    public enum Severity {
        WARNING,
        CAUTION
    }
}
