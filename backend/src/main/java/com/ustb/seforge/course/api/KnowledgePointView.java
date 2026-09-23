package com.ustb.seforge.course.api;

public record KnowledgePointView(Long id, Long chapterId, String title, String description, int sortOrder) {
}
