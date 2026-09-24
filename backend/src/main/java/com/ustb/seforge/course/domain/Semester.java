package com.ustb.seforge.course.domain;

import com.ustb.seforge.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "semesters")
public class Semester extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SemesterStatus status;

    protected Semester() {
    }

    public Semester(String code, String name, LocalDate startsOn, LocalDate endsOn, SemesterStatus status) {
        this.code = code;
        this.name = name;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.status = status;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public SemesterStatus getStatus() { return status; }

    public void update(String name, LocalDate startsOn, LocalDate endsOn, SemesterStatus status) {
        this.name = name.trim();
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.status = status;
    }
}
