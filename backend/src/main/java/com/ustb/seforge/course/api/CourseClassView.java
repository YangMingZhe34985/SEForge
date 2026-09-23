package com.ustb.seforge.course.api;

public record CourseClassView(Long id, String code, String name, Integer capacity, boolean primaryClass) {
}
