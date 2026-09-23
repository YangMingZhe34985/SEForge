package com.ustb.seforge.assignment.service;

import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.domain.KnowledgePoint;
import com.ustb.seforge.course.repository.KnowledgePointRepository;
import com.ustb.seforge.course.service.CourseAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgePointTool {
    private final CourseAccessService access;
    private final KnowledgePointRepository knowledgePoints;

    public KnowledgePointTool(CourseAccessService access, KnowledgePointRepository knowledgePoints) {
        this.access = access;
        this.knowledgePoints = knowledgePoints;
    }

    @Transactional(readOnly = true)
    public Description describe(Long courseId, Long knowledgePointId, Long userId) {
        access.requireMember(courseId, userId);
        if (knowledgePointId == null) return null;
        KnowledgePoint value = knowledgePoints.findById(knowledgePointId)
                .filter(point -> point.getCourseId().equals(courseId))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Knowledge point not found"));
        return new Description(value.getId(), value.getTitle(), value.getDescription());
    }

    public record Description(Long id, String title, String description) {
    }
}
