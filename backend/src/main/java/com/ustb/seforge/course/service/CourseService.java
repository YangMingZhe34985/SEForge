package com.ustb.seforge.course.service;

import com.ustb.seforge.common.api.PageResponse;
import com.ustb.seforge.common.exception.AppException;
import com.ustb.seforge.common.exception.ErrorCode;
import com.ustb.seforge.course.api.CourseChapterView;
import com.ustb.seforge.course.api.CourseClassView;
import com.ustb.seforge.course.api.CourseDetailsView;
import com.ustb.seforge.course.api.CourseInviteView;
import com.ustb.seforge.course.api.CourseMemberView;
import com.ustb.seforge.course.api.CourseResourceView;
import com.ustb.seforge.course.api.CourseSummaryView;
import com.ustb.seforge.course.api.CreateChapterRequest;
import com.ustb.seforge.course.api.CreateCourseClassRequest;
import com.ustb.seforge.course.api.CreateCourseRequest;
import com.ustb.seforge.course.api.CreateCourseResourceRequest;
import com.ustb.seforge.course.api.CreateInviteRequest;
import com.ustb.seforge.course.api.CreateKnowledgePointRequest;
import com.ustb.seforge.course.api.CreateSemesterRequest;
import com.ustb.seforge.course.api.KnowledgePointView;
import com.ustb.seforge.course.api.SemesterView;
import com.ustb.seforge.course.api.UpdateCourseRequest;
import com.ustb.seforge.course.api.UpdateCourseClassRequest;
import com.ustb.seforge.course.api.UpdateCourseMemberRequest;
import com.ustb.seforge.course.api.UpdateSemesterRequest;
import com.ustb.seforge.course.api.AdminUserMembershipView;
import com.ustb.seforge.course.domain.Course;
import com.ustb.seforge.course.domain.CourseChapter;
import com.ustb.seforge.course.domain.CourseClass;
import com.ustb.seforge.course.domain.CourseInvite;
import com.ustb.seforge.course.domain.CourseMember;
import com.ustb.seforge.course.domain.CourseMemberRole;
import com.ustb.seforge.course.domain.CourseMemberStatus;
import com.ustb.seforge.course.domain.CourseResource;
import com.ustb.seforge.course.domain.CourseStatus;
import com.ustb.seforge.course.domain.KnowledgePoint;
import com.ustb.seforge.course.domain.ResourceStatus;
import com.ustb.seforge.course.domain.Semester;
import com.ustb.seforge.course.repository.CourseChapterRepository;
import com.ustb.seforge.course.repository.CourseClassRepository;
import com.ustb.seforge.course.repository.CourseInviteRepository;
import com.ustb.seforge.course.repository.CourseMemberRepository;
import com.ustb.seforge.course.repository.CourseRepository;
import com.ustb.seforge.course.repository.CourseResourceRepository;
import com.ustb.seforge.course.repository.KnowledgePointRepository;
import com.ustb.seforge.course.repository.SemesterRepository;
import com.ustb.seforge.identity.service.IdentityService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseService {
    private static final String INVITE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int INVITE_LENGTH = 10;
    private static final long MAX_RESOURCE_BYTES = 200L * 1024 * 1024;

    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final CourseClassRepository classRepository;
    private final CourseMemberRepository memberRepository;
    private final CourseInviteRepository inviteRepository;
    private final CourseChapterRepository chapterRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final CourseResourceRepository resourceRepository;
    private final CourseAccessService accessService;
    private final IdentityService identityService;
    private final SecureRandom secureRandom = new SecureRandom();

    public CourseService(
            SemesterRepository semesterRepository,
            CourseRepository courseRepository,
            CourseClassRepository classRepository,
            CourseMemberRepository memberRepository,
            CourseInviteRepository inviteRepository,
            CourseChapterRepository chapterRepository,
            KnowledgePointRepository knowledgePointRepository,
            CourseResourceRepository resourceRepository,
            CourseAccessService accessService,
            IdentityService identityService) {
        this.semesterRepository = semesterRepository;
        this.courseRepository = courseRepository;
        this.classRepository = classRepository;
        this.memberRepository = memberRepository;
        this.inviteRepository = inviteRepository;
        this.chapterRepository = chapterRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.resourceRepository = resourceRepository;
        this.accessService = accessService;
        this.identityService = identityService;
    }

    @Transactional
    public SemesterView createSemester(CreateSemesterRequest request) {
        if (!request.endsOn().isAfter(request.startsOn())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Semester end date must be after start date");
        }
        if (semesterRepository.existsByCodeIgnoreCase(request.code().trim())) {
            throw new AppException(ErrorCode.CONFLICT, "Semester code already exists");
        }
        if (request.status() == com.ustb.seforge.course.domain.SemesterStatus.ACTIVE
                && semesterRepository.existsByStatus(com.ustb.seforge.course.domain.SemesterStatus.ACTIVE)) {
            throw new AppException(ErrorCode.CONFLICT, "Another semester is already current");
        }
        Semester semester = semesterRepository.save(new Semester(
                request.code().trim().toUpperCase(), request.name().trim(), request.startsOn(), request.endsOn(),
                request.status()));
        return semesterView(semester);
    }

    @Transactional(readOnly = true)
    public List<SemesterView> listSemesters() {
        return semesterRepository.findAllByOrderByStartsOnDesc().stream().map(this::semesterView).toList();
    }

    @Transactional
    public SemesterView updateSemester(Long semesterId, UpdateSemesterRequest request) {
        if (!request.endsOn().isAfter(request.startsOn())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Semester end date must be after start date");
        }
        Semester semester = requireSemester(semesterId);
        if (request.status() == com.ustb.seforge.course.domain.SemesterStatus.ACTIVE
                && semesterRepository.existsByStatusAndIdNot(request.status(), semesterId)) {
            throw new AppException(ErrorCode.CONFLICT, "Another semester is already current");
        }
        semester.update(request.name(), request.startsOn(), request.endsOn(), request.status());
        return semesterView(semester);
    }

    @Transactional
    public CourseDetailsView createCourse(Long actorId, CreateCourseRequest request) {
        if (!identityService.isTeacher(actorId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Only teacher accounts can create courses");
        }
        Semester semester = requireSemester(request.semesterId());
        String code = request.code().trim().toUpperCase();
        if (courseRepository.existsByCodeIgnoreCase(code)) {
            throw new AppException(ErrorCode.COURSE_CODE_ALREADY_EXISTS, "Course code already exists");
        }
        Course course = courseRepository.save(new Course(
                code, request.name().trim(), trimNullable(request.description()), semester.getId(), actorId));
        memberRepository.save(new CourseMember(course.getId(), null, actorId, CourseMemberRole.TEACHER));
        return courseDetails(course, actorId);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryView> listCourses(Long actorId, int page, int size) {
        return listCourses(actorId, page, size, null, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseSummaryView> listCourses(Long actorId, int page, int size,
                                                        String search, CourseStatus status, Long semesterId) {
        PageRequest pageable = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        String pattern = search == null || search.isBlank() ? null
                : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        Page<Course> courses = accessService.isAdmin(actorId)
                ? courseRepository.searchAll(pattern, status, semesterId, pageable)
                : courseRepository.findVisibleToUser(actorId, CourseMemberStatus.ACTIVE, pageable);
        List<CourseSummaryView> views = courses.stream().map(course -> courseSummary(course, actorId)).toList();
        return new PageResponse<>(views, courses.getNumber(), courses.getSize(), courses.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CourseDetailsView getCourse(Long courseId, Long actorId) {
        accessService.requireMember(courseId, actorId);
        return courseDetails(requireCourse(courseId), actorId);
    }

    @Transactional(readOnly = true)
    public List<AdminUserMembershipView> adminMemberships(Long targetUserId, Long actorId) {
        if (!accessService.isAdmin(actorId)) throw new AppException(ErrorCode.ACCESS_DENIED, "Administrator required");
        identityService.getUser(targetUserId);
        return memberRepository.findAllByUserIdAndStatus(targetUserId, CourseMemberStatus.ACTIVE).stream()
                .map(member -> new AdminUserMembershipView(member.getCourseId(),
                        requireCourse(member.getCourseId()).getName(), member.getClassId(), member.getRole()))
                .toList();
    }

    @Transactional
    public CourseDetailsView updateCourse(Long courseId, Long actorId, UpdateCourseRequest request) {
        accessService.requireTeacherOrAdmin(courseId, actorId);
        Course course = requireCourse(courseId);
        course.update(request.name(), request.description(), request.status());
        return courseDetails(course, actorId);
    }

    @Transactional
    public CourseDetailsView adminUpdateCourse(Long courseId, Long actorId, UpdateCourseRequest request) {
        if (!accessService.isAdmin(actorId)) throw new AppException(ErrorCode.ACCESS_DENIED, "Administrator required");
        Course course = requireCourse(courseId);
        course.update(request.name(), request.description(), request.status());
        return courseDetails(course, actorId);
    }

    @Transactional
    public CourseDetailsView transferOwner(Long courseId, Long actorId, Long newOwnerId) {
        if (!accessService.isAdmin(actorId)) throw new AppException(ErrorCode.ACCESS_DENIED, "Administrator required");
        if (!identityService.isTeacher(newOwnerId) || !identityService.getUser(newOwnerId).enabled()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "New owner must have teacher qualification");
        }
        Course course = requireCourse(courseId);
        CourseMember member = memberRepository.findByCourseIdAndUserId(courseId, newOwnerId).orElse(null);
        if (member == null) {
            memberRepository.save(new CourseMember(courseId, null, newOwnerId, CourseMemberRole.TEACHER));
        } else if (member.getStatus() != CourseMemberStatus.ACTIVE) {
            member.reactivate(member.getClassId(), CourseMemberRole.TEACHER);
        } else {
            member.update(member.getClassId(), CourseMemberRole.TEACHER);
        }
        course.transferOwner(newOwnerId);
        return courseDetails(course, actorId);
    }

    @Transactional
    public CourseClassView createClass(Long courseId, Long actorId, CreateCourseClassRequest request) {
        accessService.requireTeacherOrAdmin(courseId, actorId);
        requireCourse(courseId);
        String code = request.code().trim().toUpperCase();
        if (classRepository.existsByCourseIdAndCodeIgnoreCase(courseId, code)) {
            throw new AppException(ErrorCode.CONFLICT, "Class code already exists in this course");
        }
        CourseClass courseClass = classRepository.save(new CourseClass(
                courseId, code, request.name().trim(), request.capacity(), request.primaryClass()));
        return classView(courseClass);
    }

    @Transactional(readOnly = true)
    public List<CourseClassView> listClasses(Long courseId, Long actorId) {
        accessService.requireMember(courseId, actorId);
        return classRepository.findAllByCourseIdOrderByNameAsc(courseId).stream().map(this::classView).toList();
    }

    @Transactional
    public CourseClassView updateClass(Long courseId, Long classId, Long actorId,
                                       UpdateCourseClassRequest request) {
        accessService.requireTeacherOrAdmin(courseId, actorId);
        CourseClass courseClass = requireClass(courseId, classId);
        long memberCount = memberRepository.countByCourseIdAndClassIdAndStatus(
                courseId, classId, CourseMemberStatus.ACTIVE);
        if (request.capacity() != null && request.capacity() < memberCount) {
            throw new AppException(ErrorCode.CONFLICT, "Class capacity cannot be lower than active membership");
        }
        if (!request.active() && memberCount > 0) {
            throw new AppException(ErrorCode.CONFLICT, "Remove or transfer members before closing the class");
        }
        courseClass.update(request.name(), request.capacity(), request.primaryClass());
        if (request.active()) courseClass.reopen(); else courseClass.close();
        return classView(courseClass);
    }

    @Transactional
    public CourseInviteView createInvite(Long courseId, Long actorId, CreateInviteRequest request) {
        accessService.requireTeacherOrAdmin(courseId, actorId);
        requireCourse(courseId);
        if (request.classId() != null) requireClass(courseId, request.classId());
        CourseMemberRole role = request.memberRole() == null ? CourseMemberRole.STUDENT : request.memberRole();
        if (role != CourseMemberRole.STUDENT) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Invites may only grant student membership");
        }
        CourseInvite invite = inviteRepository.save(new CourseInvite(
                generateInviteCode(), courseId, request.classId(), role, request.maxUses(), request.expiresAt(), actorId));
        return inviteView(invite);
    }

    @Transactional(readOnly = true)
    public List<CourseInviteView> listInvites(Long courseId, Long actorId) {
        accessService.requireTeacherOrAdmin(courseId, actorId);
        return inviteRepository.findAllByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(this::inviteView).toList();
    }

    @Transactional
    public CourseInviteView revokeInvite(Long courseId, Long inviteId, Long actorId) {
        accessService.requireTeacherOrAdmin(courseId, actorId);
        CourseInvite invite = inviteRepository.findById(inviteId)
                .filter(value -> value.getCourseId().equals(courseId))
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Invite not found"));
        invite.revoke();
        return inviteView(invite);
    }

    @Transactional
    public CourseDetailsView joinCourse(Long actorId, String inviteCode) {
        CourseInvite invite = inviteRepository.findByCodeForUpdate(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new AppException(ErrorCode.INVITE_INVALID, "Invite code is invalid"));
        Instant now = Instant.now();
        if (invite.isExpired(now)) {
            throw new AppException(ErrorCode.INVITE_EXPIRED, "Invite code has expired");
        }
        if (invite.isExhausted()) {
            throw new AppException(ErrorCode.INVITE_EXHAUSTED, "Invite code has reached its usage limit");
        }
        if (!invite.isActive()) {
            throw new AppException(ErrorCode.INVITE_INVALID, "Invite code is inactive");
        }
        Course course = requireCourse(invite.getCourseId());
        if (course.getStatus() != CourseStatus.ACTIVE) {
            throw new AppException(ErrorCode.CONFLICT, "Course is not accepting members");
        }
        CourseMember member = memberRepository.findByCourseIdAndUserId(course.getId(), actorId).orElse(null);
        if (member != null && member.getStatus() == CourseMemberStatus.ACTIVE) {
            throw new AppException(ErrorCode.ALREADY_COURSE_MEMBER, "You are already a course member");
        }
        if (invite.getClassId() != null) {
            CourseClass courseClass = requireClassForUpdate(course.getId(), invite.getClassId());
            if (!courseClass.isActive()) throw new AppException(ErrorCode.INVITE_INVALID, "Class is closed");
            Integer capacity = courseClass.getCapacity();
            if (capacity != null && memberRepository.countByCourseIdAndClassIdAndStatus(
                    course.getId(), courseClass.getId(), CourseMemberStatus.ACTIVE) >= capacity) {
                throw new AppException(ErrorCode.CONFLICT, "Course class has reached its capacity");
            }
        }
        if (member == null) {
            memberRepository.save(new CourseMember(
                    course.getId(), invite.getClassId(), actorId, invite.getMemberRole()));
        } else {
            member.reactivate(invite.getClassId(), invite.getMemberRole());
        }
        invite.consume();
        return courseDetails(course, actorId);
    }

    @Transactional(readOnly = true)
    public List<CourseMemberView> listMembers(Long courseId, Long actorId) {
        accessService.requireTeachingStaff(courseId, actorId);
        List<CourseMember> members = memberRepository.findAllByCourseIdAndStatusOrderByJoinedAtAsc(
                courseId, CourseMemberStatus.ACTIVE);
        Map<Long, String> names = identityService.displayNames(members.stream().map(CourseMember::getUserId).toList());
        return members.stream().map(member -> new CourseMemberView(
                member.getId(), member.getUserId(), names.getOrDefault(member.getUserId(), "Unknown user"),
                member.getClassId(), member.getRole(), member.getJoinedAt())).toList();
    }

    @Transactional
    public void removeMember(Long courseId, Long targetUserId, Long actorId) {
        accessService.requireTeacherOrAdmin(courseId, actorId);
        Course course = requireCourse(courseId);
        if (course.getOwnerId().equals(targetUserId)) {
            throw new AppException(ErrorCode.CONFLICT, "The course owner cannot be removed");
        }
        CourseMember member = memberRepository.findByCourseIdAndUserIdAndStatus(
                        courseId, targetUserId, CourseMemberStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course member not found"));
        member.remove();
    }

    @Transactional
    public CourseMemberView updateMember(Long courseId, Long targetUserId, Long actorId,
                                         UpdateCourseMemberRequest request) {
        accessService.requireTeacherOrAdmin(courseId, actorId);
        Course course = requireCourse(courseId);
        if (course.getOwnerId().equals(targetUserId) || request.role() == CourseMemberRole.TEACHER) {
            throw new AppException(ErrorCode.CONFLICT, "Course owner and teacher role require administrator transfer");
        }
        CourseMember member = memberRepository.findByCourseIdAndUserIdAndStatus(
                        courseId, targetUserId, CourseMemberStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course member not found"));
        if (request.classId() != null) {
            CourseClass courseClass = requireClassForUpdate(courseId, request.classId());
            if (!courseClass.isActive()) throw new AppException(ErrorCode.CONFLICT, "Class is closed");
            if (!request.classId().equals(member.getClassId()) && courseClass.getCapacity() != null
                    && memberRepository.countByCourseIdAndClassIdAndStatus(
                    courseId, request.classId(), CourseMemberStatus.ACTIVE) >= courseClass.getCapacity()) {
                throw new AppException(ErrorCode.CONFLICT, "Class has reached its capacity");
            }
        }
        member.update(request.classId(), request.role());
        return new CourseMemberView(member.getId(), member.getUserId(),
                identityService.displayNames(List.of(targetUserId)).getOrDefault(targetUserId, "Unknown user"),
                member.getClassId(), member.getRole(), member.getJoinedAt());
    }

    @Transactional
    public CourseChapterView createChapter(Long courseId, Long actorId, CreateChapterRequest request) {
        accessService.requireTeachingStaff(courseId, actorId);
        requireCourse(courseId);
        if (request.parentId() != null) requireChapter(courseId, request.parentId());
        return chapterView(chapterRepository.save(new CourseChapter(
                courseId, request.parentId(), request.title().trim(), trimNullable(request.description()),
                request.sortOrder())));
    }

    @Transactional(readOnly = true)
    public List<CourseChapterView> listChapters(Long courseId, Long actorId) {
        accessService.requireMember(courseId, actorId);
        return chapterRepository.findAllByCourseIdOrderBySortOrderAscIdAsc(courseId).stream()
                .map(this::chapterView).toList();
    }

    @Transactional
    public CourseChapterView updateChapter(Long courseId, Long chapterId, Long actorId,
                                           CreateChapterRequest request) {
        accessService.requireTeachingStaff(courseId, actorId);
        CourseChapter chapter = requireChapter(courseId, chapterId);
        if (request.parentId() != null) {
            Set<Long> visited = new HashSet<>();
            Long ancestorId = request.parentId();
            while (ancestorId != null) {
                if (ancestorId.equals(chapterId) || !visited.add(ancestorId)) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED, "Chapter parent would create a cycle");
                }
                ancestorId = requireChapter(courseId, ancestorId).getParentId();
            }
        }
        chapter.update(request.parentId(), request.title(), trimNullable(request.description()), request.sortOrder());
        return chapterView(chapter);
    }

    @Transactional
    public void deleteChapter(Long courseId, Long chapterId, Long actorId) {
        accessService.requireTeachingStaff(courseId, actorId);
        CourseChapter chapter = requireChapter(courseId, chapterId);
        if (chapterRepository.existsByParentId(chapterId) || knowledgePointRepository.existsByChapterId(chapterId)
                || resourceRepository.existsByChapterIdAndStatus(chapterId, ResourceStatus.ACTIVE)) {
            throw new AppException(ErrorCode.CONFLICT, "Chapter still has dependent content");
        }
        chapterRepository.delete(chapter);
    }

    @Transactional
    public KnowledgePointView createKnowledgePoint(
            Long courseId, Long actorId, CreateKnowledgePointRequest request) {
        accessService.requireTeachingStaff(courseId, actorId);
        requireCourse(courseId);
        if (request.chapterId() != null) requireChapter(courseId, request.chapterId());
        return knowledgePointView(knowledgePointRepository.save(new KnowledgePoint(
                courseId, request.chapterId(), request.title().trim(), trimNullable(request.description()),
                request.sortOrder())));
    }

    @Transactional(readOnly = true)
    public List<KnowledgePointView> listKnowledgePoints(Long courseId, Long actorId) {
        accessService.requireMember(courseId, actorId);
        return knowledgePointRepository.findAllByCourseIdOrderBySortOrderAscIdAsc(courseId).stream()
                .map(this::knowledgePointView).toList();
    }

    @Transactional
    public KnowledgePointView updateKnowledgePoint(Long courseId, Long pointId, Long actorId,
                                                    CreateKnowledgePointRequest request) {
        accessService.requireTeachingStaff(courseId, actorId);
        KnowledgePoint point = requireKnowledgePoint(courseId, pointId);
        if (request.chapterId() != null) requireChapter(courseId, request.chapterId());
        point.update(request.chapterId(), request.title(), trimNullable(request.description()), request.sortOrder());
        return knowledgePointView(point);
    }

    @Transactional
    public void deleteKnowledgePoint(Long courseId, Long pointId, Long actorId) {
        accessService.requireTeachingStaff(courseId, actorId);
        knowledgePointRepository.delete(requireKnowledgePoint(courseId, pointId));
    }

    @Transactional
    public CourseResourceView createResource(
            Long courseId, Long actorId, CreateCourseResourceRequest request) {
        accessService.requireTeachingStaff(courseId, actorId);
        requireCourse(courseId);
        if (request.chapterId() != null) requireChapter(courseId, request.chapterId());
        validateObjectKey(courseId, request.objectKey());
        if (request.sizeBytes() != null && request.sizeBytes() > MAX_RESOURCE_BYTES) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Resource exceeds the 200 MB limit");
        }
        if (resourceRepository.existsByObjectKey(request.objectKey())) {
            throw new AppException(ErrorCode.CONFLICT, "Object key is already registered");
        }
        CourseResource resource = resourceRepository.save(new CourseResource(
                courseId, request.chapterId(), actorId, request.name().trim(), trimNullable(request.description()),
                request.resourceType(), request.objectKey(), request.contentType(), request.sizeBytes()));
        return resourceView(resource);
    }

    @Transactional(readOnly = true)
    public List<CourseResourceView> listResources(Long courseId, Long actorId) {
        accessService.requireMember(courseId, actorId);
        return resourceRepository.findAllByCourseIdAndStatusOrderByCreatedAtDesc(courseId, ResourceStatus.ACTIVE)
                .stream().map(this::resourceView).toList();
    }

    @Transactional(readOnly = true)
    public CourseResourceView getResource(Long courseId, Long resourceId, Long actorId) {
        accessService.requireMember(courseId, actorId);
        CourseResource resource = resourceRepository.findByIdAndCourseIdAndStatus(
                        resourceId, courseId, ResourceStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course resource not found"));
        return resourceView(resource);
    }

    @Transactional
    public void removeResource(Long courseId, Long resourceId, Long actorId) {
        accessService.requireTeachingStaff(courseId, actorId);
        CourseResource resource = resourceRepository.findByIdAndCourseIdAndStatus(
                        resourceId, courseId, ResourceStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course resource not found"));
        resource.remove();
    }

    private Course requireCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course not found"));
    }

    private Semester requireSemester(Long id) {
        return semesterRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Semester not found"));
    }

    private CourseClass requireClass(Long courseId, Long classId) {
        CourseClass courseClass = classRepository.findById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course class not found"));
        if (!courseClass.getCourseId().equals(courseId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course class not found");
        }
        return courseClass;
    }

    private CourseClass requireClassForUpdate(Long courseId, Long classId) {
        CourseClass courseClass = classRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course class not found"));
        if (!courseClass.getCourseId().equals(courseId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course class not found");
        }
        return courseClass;
    }

    private CourseChapter requireChapter(Long courseId, Long chapterId) {
        CourseChapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course chapter not found"));
        if (!chapter.getCourseId().equals(courseId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Course chapter not found");
        }
        return chapter;
    }

    private KnowledgePoint requireKnowledgePoint(Long courseId, Long pointId) {
        KnowledgePoint point = knowledgePointRepository.findById(pointId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Knowledge point not found"));
        if (!point.getCourseId().equals(courseId)) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Knowledge point not found");
        }
        return point;
    }

    private String generateInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder(INVITE_LENGTH);
            for (int index = 0; index < INVITE_LENGTH; index++) {
                code.append(INVITE_ALPHABET.charAt(secureRandom.nextInt(INVITE_ALPHABET.length())));
            }
            if (!inviteRepository.existsByCodeIgnoreCase(code.toString())) return code.toString();
        }
        throw new IllegalStateException("Could not allocate a unique invite code");
    }

    private void validateObjectKey(Long courseId, String objectKey) {
        String requiredPrefix = "courses/" + courseId + "/";
        if (!objectKey.startsWith(requiredPrefix) || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new AppException(
                    ErrorCode.VALIDATION_FAILED,
                    "Object key must be course-scoped under " + requiredPrefix);
        }
    }

    private CourseSummaryView courseSummary(Course course, Long actorId) {
        Semester semester = requireSemester(course.getSemesterId());
        return new CourseSummaryView(
                course.getId(), course.getCode(), course.getName(), course.getDescription(), semester.getId(),
                semester.getName(), course.getOwnerId(), course.getStatus(),
                accessService.roleFor(course.getId(), actorId).orElse(null),
                memberRepository.countByCourseIdAndStatus(course.getId(), CourseMemberStatus.ACTIVE),
                course.getCreatedAt());
    }

    private CourseDetailsView courseDetails(Course course, Long actorId) {
        Semester semester = requireSemester(course.getSemesterId());
        List<CourseClassView> classes = classRepository.findAllByCourseIdOrderByNameAsc(course.getId()).stream()
                .map(this::classView).toList();
        return new CourseDetailsView(
                course.getId(), course.getCode(), course.getName(), course.getDescription(), semester.getId(),
                semester.getName(), course.getOwnerId(), course.getStatus(),
                accessService.roleFor(course.getId(), actorId).orElse(null),
                memberRepository.countByCourseIdAndStatus(course.getId(), CourseMemberStatus.ACTIVE), classes,
                course.getCreatedAt());
    }

    private SemesterView semesterView(Semester semester) {
        return new SemesterView(semester.getId(), semester.getCode(), semester.getName(), semester.getStartsOn(),
                semester.getEndsOn(), semester.getStatus());
    }

    private CourseClassView classView(CourseClass courseClass) {
        return new CourseClassView(courseClass.getId(), courseClass.getCode(), courseClass.getName(),
                courseClass.getCapacity(), courseClass.isPrimaryClass(), courseClass.isActive());
    }

    private CourseInviteView inviteView(CourseInvite invite) {
        return new CourseInviteView(
                invite.getId(), invite.getCode(), invite.getCourseId(), invite.getClassId(), invite.getMemberRole(),
                invite.getMaxUses(), invite.getUsedCount(), invite.getExpiresAt(), invite.isActive());
    }

    private CourseChapterView chapterView(CourseChapter chapter) {
        return new CourseChapterView(chapter.getId(), chapter.getParentId(), chapter.getTitle(),
                chapter.getDescription(), chapter.getSortOrder());
    }

    private KnowledgePointView knowledgePointView(KnowledgePoint point) {
        return new KnowledgePointView(
                point.getId(), point.getChapterId(), point.getTitle(), point.getDescription(), point.getSortOrder());
    }

    private CourseResourceView resourceView(CourseResource resource) {
        return new CourseResourceView(
                resource.getId(), resource.getCourseId(), resource.getChapterId(), resource.getUploaderId(),
                resource.getName(), resource.getDescription(), resource.getResourceType(), resource.getObjectKey(),
                resource.getContentType(), resource.getSizeBytes(), resource.getCreatedAt());
    }

    private String trimNullable(String value) {
        return value == null ? null : value.trim();
    }
}
