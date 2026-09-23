package com.ustb.seforge.content.api;

import com.ustb.seforge.job.api.AsyncJobView;

public record DocumentUploadView(KnowledgeDocumentView document, AsyncJobView job, boolean duplicate) {
}
