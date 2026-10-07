package com.marketplace.backend.repository;

import com.marketplace.backend.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"})
class JobDiscoveryRepositoryTest {
    @Autowired JobRepository jobs;
    @Autowired JobApplicationRepository applications;
    @Autowired TestEntityManager em;
    final UUID freelancer = UUID.randomUUID();

    Job add(String title, JobCategory category, String budget, String... skills) {
        Job row = new Job(); row.setTitle(title); row.setDescription("Real brief"); row.setCategory(category);
        row.setSkills(new ArrayList<>(List.of(skills))); row.setClientUserId(UUID.randomUUID());
        row.setBudgetUsd(new BigDecimal(budget)); row.setStatus(JobStatus.OPEN);
        return jobs.saveAndFlush(row);
    }

    @Test void categorySearchBudgetsAndSortComposeAgainstAllJobs() {
        Job selected = add("Payments API", JobCategory.BACKEND_API, "500", "Java");
        add("Payments API too expensive", JobCategory.BACKEND_API, "900", "Java");
        add("Payments API wrong category", JobCategory.WEB_FRONTEND, "450", "React");
        add("Unrelated backend", JobCategory.BACKEND_API, "450", "Java");
        var page = jobs.discover(freelancer, "payments", new BigDecimal("400"), new BigDecimal("600"), "ALL",
                JobCategory.BACKEND_API, false, List.of(""), PageRequest.of(0, 1, Sort.by("budgetUsd")));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Job::getId).containsExactly(selected.getId());
    }

    @Test void skillsMatchAnyCaseInsensitiveWithoutDuplicatingRowsOrPageTotals() {
        Job both = add("API", JobCategory.BACKEND_API, "100", "Java", "Spring");
        Job one = add("Other API", JobCategory.BACKEND_API, "200", "Java");
        add("UI", JobCategory.WEB_FRONTEND, "300", "React");
        var page = jobs.discover(freelancer, null, null, null, "ALL", null, true, List.of("java", "spring"),
                PageRequest.of(0, 1, Sort.by("budgetUsd")));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Job::getId).containsExactly(both.getId());
        assertThat(jobs.discover(freelancer, null, null, null, "ALL", null, true, List.of("java", "spring"),
                PageRequest.of(1, 1, Sort.by("budgetUsd"))).getContent()).extracting(Job::getId).containsExactly(one.getId());
        em.clear();
        assertThat(jobs.findById(both.getId()).orElseThrow().getSkills()).containsExactly("Java", "Spring");
    }

    @Test void applicationFiltersAndOpenUnassignedEligibilityRemainIntact() {
        Job applied = add("API applied", JobCategory.BACKEND_API, "100", "Java");
        Job available = add("API new", JobCategory.BACKEND_API, "100", "Java");
        Job closed = add("API closed", JobCategory.BACKEND_API, "100", "Java"); closed.setStatus(JobStatus.COMPLETED);
        Job assigned = add("API assigned", JobCategory.BACKEND_API, "100", "Java"); assigned.setFreelancerId(UUID.randomUUID());
        JobApplication application = new JobApplication(); application.setJobId(applied.getId()); application.setFreelancerId(freelancer);
        application.setStatus(JobApplicationStatus.REJECTED); applications.saveAndFlush(application); em.flush();
        for (String filter : List.of("ALL", "APPLIED", "NOT_APPLIED")) {
            var result = jobs.discover(freelancer, "api", null, null, filter, JobCategory.BACKEND_API, true,
                    List.of("java"), PageRequest.of(0, 10));
            assertThat(result.getContent()).extracting(Job::getId).containsExactlyInAnyOrderElementsOf(
                    filter.equals("ALL") ? List.of(applied.getId(), available.getId()) :
                            filter.equals("APPLIED") ? List.of(applied.getId()) : List.of(available.getId()));
        }
    }

    @Test void legacyRowUsesDatabaseOtherDefaultAndEmptySkillsWithoutFabrication() {
        UUID id = UUID.randomUUID();
        em.getEntityManager().createNativeQuery("insert into jobs (id,title,budget_usd,client_user_id,status,review_window_hours,max_revisions) values (?1,'Legacy landing page',100,?2,'OPEN',72,2)")
                .setParameter(1, id).setParameter(2, UUID.randomUUID()).executeUpdate();
        em.clear();
        Job legacy = jobs.findById(id).orElseThrow();
        assertThat(legacy.getCategory()).isEqualTo(JobCategory.OTHER); assertThat(legacy.getSkills()).isEmpty();
        assertThat(jobs.discover(freelancer, null, null, null, "ALL", JobCategory.OTHER, false, List.of(""), PageRequest.of(0, 10)).getContent())
                .extracting(Job::getId).containsExactly(id);
        assertThat(jobs.discover(freelancer, null, null, null, "ALL", JobCategory.OTHER, true, List.of("react"), PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test void metadataEditsAndSkillClearingPersistWithoutChangingWorkflowOrBudget() {
        Job row = add("Existing job", JobCategory.BACKEND_API, "250", "Java", "Spring");
        UUID id = row.getId(); em.clear();
        Job editable = jobs.findById(id).orElseThrow();
        editable.setCategory(JobCategory.MOBILE_APP); editable.setSkills(new ArrayList<>(List.of("Flutter", "Dart")));
        jobs.saveAndFlush(editable); em.clear();
        Job saved = jobs.findById(id).orElseThrow();
        assertThat(saved.getCategory()).isEqualTo(JobCategory.MOBILE_APP);
        assertThat(saved.getSkills()).containsExactly("Flutter", "Dart");
        assertThat(saved.getBudgetUsd()).isEqualByComparingTo("250"); assertThat(saved.getStatus()).isEqualTo(JobStatus.OPEN);
        saved.setSkills(new ArrayList<>()); jobs.saveAndFlush(saved); em.clear();
        assertThat(jobs.findById(id).orElseThrow().getSkills()).isEmpty();
    }
}
