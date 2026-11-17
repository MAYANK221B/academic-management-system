package com.acadence.repository;

import com.acadence.model.*;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

import static org.assertj.core.api.Assertions.assertThatCode;

class CourseMembershipMappingTest {

    @Test
    void hibernateAcceptsMembershipRelationships() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false).build();
        try {
            var metadata = (MetadataImplementor) new MetadataSources(registry).addAnnotatedClass(User.class)
                    .addAnnotatedClass(Course.class).addAnnotatedClass(CourseOffering.class)
                    .addAnnotatedClass(CourseMembership.class).buildMetadata();
            assertThatCode(metadata::validate).doesNotThrowAnyException();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void derivedRepositoryQueriesResolveRelationshipPaths() {
        for (var method : CourseMembershipRepository.class.getDeclaredMethods()) {
            assertThatCode(() -> new PartTree(method.getName(), CourseMembership.class)).doesNotThrowAnyException();
        }
    }
}
