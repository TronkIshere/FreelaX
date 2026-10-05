package com.marketplace.backend.repository;

import com.marketplace.backend.entity.MemberProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
class MemberProfileRepositoryTest {
    @Autowired MemberProfileRepository profiles;
    @Autowired TestEntityManager entityManager;

    @Test
    void profileVersionAdvancesAndDatabaseRejectsDuplicateOwner() {
        UUID owner = UUID.randomUUID();
        MemberProfile first = new MemberProfile(); first.setUserId(owner);
        profiles.saveAndFlush(first);
        assertThat(first.getVersion()).isZero();
        first.setHeadline("Engineer");
        profiles.saveAndFlush(first);
        assertThat(first.getVersion()).isEqualTo(1);
        entityManager.clear();
        MemberProfile duplicate = new MemberProfile(); duplicate.setUserId(owner);
        assertThatThrownBy(() -> profiles.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
