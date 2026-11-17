package com.acadence.repository;

import com.acadence.enums.Semester;
import com.acadence.model.Course;
import com.acadence.model.CourseOffering;
import org.flywaydb.core.Flyway;
import org.hibernate.Hibernate;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in test against a dedicated PostgreSQL test database; never uses application credentials. */
@EnabledIfEnvironmentVariable(named = "ACADENCE_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class CoursePostgresTest {

    @Test
    void migratesValidatesAndPersistsCourseFoundation() throws Exception {
        String username = System.getenv("ACADENCE_TEST_DATABASE_USERNAME");
        String password = System.getenv("ACADENCE_TEST_DATABASE_PASSWORD");
        assumeTrue(username != null && password != null, "Dedicated test credentials are required");
        var datasource = new DriverManagerDataSource(System.getenv("ACADENCE_TEST_DATABASE_URL"), username, password);
        String schema = "acadence_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            var result = Flyway.configure().dataSource(datasource).defaultSchema(schema).schemas(schema)
                    .locations("classpath:db/migration").target("2").load().migrate();
            assertThat(result.migrationsExecuted).isEqualTo(2);

            var registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.datasource", datasource)
                    .applySetting("hibernate.default_schema", schema)
                    .applySetting("hibernate.hbm2ddl.auto", "validate")
                    .build();
            try (var factory = new MetadataSources(registry).addAnnotatedClass(Course.class)
                    .addAnnotatedClass(CourseOffering.class).buildMetadata().buildSessionFactory();
                 var entityManager = factory.createEntityManager()) {
                var repositories = new JpaRepositoryFactory(entityManager);
                var courses = repositories.getRepository(CourseRepository.class);
                var offerings = repositories.getRepository(CourseOfferingRepository.class);
                entityManager.getTransaction().begin();
                Course course = courses.saveAndFlush(Course.builder().courseCode("CS101")
                        .courseName("Introduction to Computing").description("Course description").build());
                assertThat(course.getCourseId()).isPositive();
                assertThat(course.getCreatedAt()).isNotNull();
                assertThat(course.getUpdatedAt()).isNotNull();
                assertThat(courses.existsByCourseCode("CS101")).isTrue();
                assertThat(courses.findByCourseCode("CS101")).isPresent();
                CourseOffering offering = offerings.saveAndFlush(CourseOffering.builder().course(course)
                        .semester(Semester.FALL).year(2026).section("A").build());
                assertThat(offerings.existsByCourse_CourseIdAndSemesterAndYearAndSection(
                        course.getCourseId(), Semester.FALL, 2026, "A")).isTrue();
                assertThat(offerings.existsByCourse_CourseIdAndSemesterAndYearAndSection(
                        course.getCourseId(), Semester.SPRING, 2026, "A")).isFalse();
                Long offeringId = offering.getOfferingId();
                Long courseId = course.getCourseId();
                entityManager.getTransaction().commit();
                entityManager.clear();
                CourseOffering loaded = offerings.findById(offeringId).orElseThrow();
                assertThat(loaded.getSemester()).isEqualTo(Semester.FALL);
                assertThat(Hibernate.isInitialized(loaded.getCourse())).isFalse();
                assertThat(offerings.findByCourse_CourseId(courseId)).hasSize(1);
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }

            try (var connection = datasource.getConnection(); var sql = connection.createStatement()) {
                assertThatThrownBy(() -> sql.executeUpdate("INSERT INTO " + schema
                        + ".course_offerings(course_id, semester, year, section) "
                        + "SELECT course_id, 'WINTER', 2026, 'B' FROM " + schema + ".courses"))
                        .isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(() -> sql.executeUpdate("INSERT INTO " + schema
                        + ".course_offerings(course_id, semester, year, section) "
                        + "SELECT course_id, 'FALL', 0, 'B' FROM " + schema + ".courses"))
                        .isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(() -> sql.executeUpdate("INSERT INTO " + schema
                        + ".course_offerings(course_id, semester, year, section) "
                        + "SELECT course_id, 'FALL', 2026, 'A' FROM " + schema + ".courses"))
                        .isInstanceOf(java.sql.SQLException.class);
            }
        } finally {
            // Only this test's randomly named schema is removed; public and existing history are untouched.
            try (var connection = datasource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
