package com.acadence.model;

import com.acadence.enums.Semester;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "course_offerings", uniqueConstraints = @UniqueConstraint(
        name = "uk_course_offerings_course_term_section",
        columnNames = {"course_id", "semester", "year", "section"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offering_id")
    private Long offeringId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "semester", nullable = false, length = 20)
    private Semester semester;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "section", nullable = false, length = 20)
    private String section;

    @Builder
    public CourseOffering(Course course, Semester semester, Integer year, String section) {
        this.course = course;
        this.semester = semester;
        this.year = year;
        this.section = section;
    }
}
