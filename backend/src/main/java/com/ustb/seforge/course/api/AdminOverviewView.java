package com.ustb.seforge.course.api;

public record AdminOverviewView(long teachers, long students, long activeCourses,
                                long archivedCourses, SemesterView currentSemester) {
}
