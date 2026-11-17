package com.acadence.repository;

import com.acadence.enums.AccountStatus;
import com.acadence.enums.CourseRole;
import com.acadence.enums.Semester;
import com.acadence.model.*;
import org.flywaydb.core.Flyway;
import org.hibernate.Hibernate;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@EnabledIfEnvironmentVariable(named = "ACADENCE_TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class CourseMembershipPostgresTest {

    @Test
    void migrationPersistenceQueriesAndConstraintsMatchPostgres() throws Exception {
        String username = System.getenv("ACADENCE_TEST_DATABASE_USERNAME");
        String password = System.getenv("ACADENCE_TEST_DATABASE_PASSWORD");
        assumeTrue(username != null && password != null, "Dedicated test credentials are required");
        var datasource = new DriverManagerDataSource(System.getenv("ACADENCE_TEST_DATABASE_URL"), username, password);
        String schema = "acadence_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            var migration = Flyway.configure().dataSource(datasource).defaultSchema(schema).schemas(schema)
                    .locations("classpath:db/migration").target("3").load().migrate();
            assertThat(migration.migrationsExecuted).isEqualTo(3);
            var registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.datasource", datasource)
                    .applySetting("hibernate.default_schema", schema)
                    .applySetting("hibernate.hbm2ddl.auto", "validate").build();
            Long userId;
            Long offeringId;
            try (var factory = new MetadataSources(registry).addAnnotatedClass(User.class)
                    .addAnnotatedClass(Course.class).addAnnotatedClass(CourseOffering.class)
                    .addAnnotatedClass(CourseMembership.class).getMetadataBuilder()
                    .applyPhysicalNamingStrategy(new PhysicalNamingStrategySnakeCaseImpl())
                    .build().buildSessionFactory();
                 var entityManager = factory.createEntityManager()) {
                var repository = new JpaRepositoryFactory(entityManager).getRepository(CourseMembershipRepository.class);
                entityManager.getTransaction().begin();
                User student = User.builder().fullName("Student").email("student@example.test")
                        .passwordHash("test-only-hash").accountStatus(AccountStatus.ACTIVE).build();
                User professor = User.builder().fullName("Professor").email("professor@example.test")
                        .passwordHash("test-only-hash").accountStatus(AccountStatus.ACTIVE).build();
                Course course = Course.builder().courseCode("CS101").courseName("Computing").build();
                entityManager.persist(student);
                entityManager.persist(professor);
                entityManager.persist(course);
                CourseOffering first = CourseOffering.builder().course(course).semester(Semester.FALL)
                        .year(2026).section("A").build();
                CourseOffering second = CourseOffering.builder().course(course).semester(Semester.SPRING)
                        .year(2027).section("A").build();
                entityManager.persist(first);
                entityManager.persist(second);
                CourseMembership membership = repository.saveAndFlush(CourseMembership.builder()
                        .user(student).offering(first).role(CourseRole.STUDENT).build());
                repository.saveAndFlush(CourseMembership.builder().user(professor).offering(first)
                        .role(CourseRole.PROFESSOR).build());
                repository.saveAndFlush(CourseMembership.builder().user(student).offering(second)
                        .role(CourseRole.TA).build());
                assertThat(membership.getMembershipId()).isPositive();
                assertThat(membership.getJoinedAt()).isNotNull();
                userId = student.getUserId();
                offeringId = first.getOfferingId();
                Long secondId = second.getOfferingId();
                Long membershipId = membership.getMembershipId();
                entityManager.getTransaction().commit();
                entityManager.clear();

                CourseMembership loaded = repository.findById(membershipId).orElseThrow();
                assertThat(Hibernate.isInitialized(loaded.getUser())).isFalse();
                assertThat(Hibernate.isInitialized(loaded.getOffering())).isFalse();
                assertThat(repository.existsByUser_UserIdAndOffering_OfferingId(userId, offeringId)).isTrue();
                assertThat(repository.existsByUser_UserIdAndOffering_OfferingId(userId, Long.MAX_VALUE)).isFalse();
                assertThat(repository.findByUser_UserIdAndOffering_OfferingId(userId, offeringId))
                        .get().extracting(CourseMembership::getRole).isEqualTo(CourseRole.STUDENT);
                assertThat(repository.findByOffering_OfferingId(offeringId)).extracting(CourseMembership::getRole)
                        .containsExactlyInAnyOrder(CourseRole.STUDENT, CourseRole.PROFESSOR);
                assertThat(repository.findByUser_UserId(userId)).extracting(CourseMembership::getRole)
                        .containsExactlyInAnyOrder(CourseRole.STUDENT, CourseRole.TA);
                assertThat(repository.findByUser_UserId(userId))
                        .extracting(member -> member.getOffering().getOfferingId())
                        .containsExactlyInAnyOrder(offeringId, secondId);
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }

            try (var connection = datasource.getConnection(); var sql = connection.createStatement()) {
                String insert = "INSERT INTO " + schema
                        + ".course_memberships(user_id, offering_id, role, joined_at) VALUES ";
                assertSqlState(() -> sql.executeUpdate(insert + "(" + userId + "," + offeringId
                        + ",'TA',CURRENT_TIMESTAMP)"), "23505");
                assertSqlState(() -> sql.executeUpdate(insert + "(" + Long.MAX_VALUE + "," + offeringId
                        + ",'STUDENT',CURRENT_TIMESTAMP)"), "23503");
                assertSqlState(() -> sql.executeUpdate(insert + "(" + userId + "," + Long.MAX_VALUE
                        + ",'STUDENT',CURRENT_TIMESTAMP)"), "23503");
                assertSqlState(() -> sql.executeUpdate("UPDATE " + schema
                        + ".course_memberships SET role='ADMIN'"), "23514");
                assertSqlState(() -> sql.executeUpdate("DELETE FROM " + schema
                        + ".users WHERE user_id=" + userId), "23503");
                assertSqlState(() -> sql.executeUpdate("DELETE FROM " + schema
                        + ".course_offerings WHERE offering_id=" + offeringId), "23503");
            }
        } finally {
            // Only this test's randomly named, disposable schema is removed.
            try (var connection = datasource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private void assertSqlState(SqlOperation operation, String expectedState) {
        SQLException error = catchThrowableOfType(SQLException.class, operation::execute);
        assertThat((Throwable) error).isNotNull();
        assertThat(error.getSQLState()).isEqualTo(expectedState);
    }

    @FunctionalInterface
    interface SqlOperation {
        void execute() throws SQLException;
    }
}
