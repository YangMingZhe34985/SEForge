package com.ustb.seforge.course.api;

public record CourseChapterView(Long id, Long parentId, String title, String description, int sortOrder) {
}
