package com.ustb.seforge.assignment.service;

import com.ustb.seforge.content.service.CourseKnowledgeSearchService;
import com.ustb.seforge.content.service.KnowledgeEvidence;
import com.ustb.seforge.course.service.CourseAccessService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CourseKnowledgeTool {
    private final CourseAccessService access;
    private final CourseKnowledgeSearchService knowledge;

    public CourseKnowledgeTool(CourseAccessService access, CourseKnowledgeSearchService knowledge) {
        this.access = access;
        this.knowledge = knowledge;
    }

    public List<KnowledgeEvidence> search(Long courseId, Long userId, String query, int limit) {
        access.requireMember(courseId, userId);
        return knowledge.search(courseId, query, Math.min(Math.max(limit, 1), 8));
    }
}
