package com.acadence.repository;

import com.acadence.model.User;
import com.acadence.model.UserPlatformRole;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;

import static org.assertj.core.api.Assertions.*;

class UserPlatformRoleMappingTest {

    @Test
    void hibernateAcceptsPlatformRoleMapping() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false).build();
        try {
            var metadata = (MetadataImplementor) new MetadataSources(registry).addAnnotatedClass(User.class)
                    .addAnnotatedClass(UserPlatformRole.class).buildMetadata();
            assertThatCode(metadata::validate).doesNotThrowAnyException();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    void existenceQueryResolvesUserRelationshipAndRole() {
        var query = new PartTree("existsByUser_UserIdAndRole", UserPlatformRole.class);
        assertThat(query.isExistsProjection()).isTrue();
        assertThat(query.getParts()).extracting(part -> part.getProperty().toDotPath())
                .containsExactly("user.userId", "role");
    }
}
