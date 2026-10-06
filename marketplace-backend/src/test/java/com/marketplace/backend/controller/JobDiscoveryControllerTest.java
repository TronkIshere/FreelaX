package com.marketplace.backend.controller;

import com.marketplace.backend.configuration.*;
import com.marketplace.backend.configuration.jwt.*;
import com.marketplace.backend.dto.response.job.JobResponse;
import com.marketplace.backend.entity.JobCategory;
import com.marketplace.backend.exception.*;
import com.marketplace.backend.service.JobService;
import com.marketplace.backend.service.JwtService;
import com.marketplace.backend.service.UserDetailsServiceCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(JobController.class)
@Import({SecurityConfiguration.class, InternalApiKeyFilter.class, JwtAuthenticationEntryPoint.class, JwtAccessDenied.class})
@TestPropertySource(properties={"cors.allowed-origins=http://localhost:8080","internal.api.key=test-only-internal-key"})
class JobDiscoveryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean JobService service;
    @MockitoBean UserDetailsServiceCustomizer userDetails;
    @MockitoBean JwtService jwt;
    final UUID actor = UUID.randomUUID();
    UserPrincipal principal() { return new UserPrincipal(actor, "client@example.test", "hash", Set.of(), true); }
    String body(String category) {
        return """
            {"title":"API","description":"Brief","budgetUsd":100,"deliveryDueAt":"2030-01-01T00:00:00Z",
             "category":%s,"skills":["Spring","Java"],
             "deliverables":[{"title":"Source","description":"API source"}],
             "acceptanceCriteria":[{"description":"Pass agreed tests"}]}
            """.formatted(category);
    }

    @Test void createAndPatchExposeCategoryAndJobSkills() throws Exception {
        JobResponse response = JobResponse.builder().id(UUID.randomUUID()).category(JobCategory.BACKEND_API).skills(List.of("Spring", "Java")).build();
        when(service.create(eq(actor), any())).thenReturn(response);
        mvc.perform(post("/api/v1/marketplace/jobs").with(user(principal())).contentType(MediaType.APPLICATION_JSON).content(body("\"BACKEND_API\"")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.category").value("BACKEND_API"))
                .andExpect(jsonPath("$.data.skills[0]").value("Spring"));
        verify(service).create(eq(actor), argThat(request -> request.getCategory().equals("BACKEND_API") && request.getSkills().equals(List.of("Spring", "Java"))));
        when(service.update(eq(actor), eq(response.getId()), any())).thenReturn(response);
        mvc.perform(patch("/api/v1/marketplace/jobs/" + response.getId()).with(user(principal())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"BACKEND_API\",\"skills\":[\"Spring\",\"Java\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.category").value("BACKEND_API"));
        verify(service).update(eq(actor), eq(response.getId()), argThat(request -> request.getBudgetUsd() == null && request.getSkills().size() == 2));
    }

    @Test void missingCategoryIsRejectedAndInvalidCategoryRemainsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/marketplace/jobs").with(user(principal())).contentType(MediaType.APPLICATION_JSON).content(body("null")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATA"));
        verifyNoInteractions(service);
        when(service.create(eq(actor), any())).thenThrow(new ApplicationException(ErrorCode.INVALID_DATA));
        mvc.perform(post("/api/v1/marketplace/jobs").with(user(principal())).contentType(MediaType.APPLICATION_JSON).content(body("\"REMOTE\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATA"));
    }

    @Test void repeatedSkillsAreLiteralAnyMatchValuesAndExistingParamsArePreserved() throws Exception {
        mvc.perform(get("/api/v1/marketplace/jobs/discover").with(user(principal()))
                        .param("keyword", "API").param("category", "BACKEND_API").param("skills", "Spring", "C, C++")
                        .param("application", "NOT_APPLIED").param("sort", "BUDGET_DESC"))
                .andExpect(status().isOk());
        verify(service).discover(eq(actor), eq(0), eq(10), eq("API"), isNull(), isNull(),
                eq("BUDGET_DESC"), eq("NOT_APPLIED"), eq("BACKEND_API"), eq(List.of("Spring", "C, C++")));
    }

    @Test void discoverWithoutNewFiltersStillUsesExistingDefaults() throws Exception {
        mvc.perform(get("/api/v1/marketplace/jobs/discover").with(user(principal()))).andExpect(status().isOk());
        verify(service).discover(eq(actor), eq(0), eq(10), isNull(), isNull(), isNull(), eq("NEWEST"), eq("ALL"), isNull(), isNull());
    }
}
