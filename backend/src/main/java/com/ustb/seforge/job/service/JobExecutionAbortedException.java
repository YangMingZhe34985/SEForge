package com.ustb.seforge.job.service;

public class JobExecutionAbortedException extends RuntimeException {
    public JobExecutionAbortedException(String message) {
        super(message);
    }
}
