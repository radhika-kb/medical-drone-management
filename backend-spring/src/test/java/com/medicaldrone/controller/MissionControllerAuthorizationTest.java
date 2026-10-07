package com.medicaldrone.controller;

import com.medicaldrone.config.SecurityConfig;
import com.medicaldrone.security.JwtTokenProvider;
import com.medicaldrone.security.UserDetailsServiceImpl;
import com.medicaldrone.service.MissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MissionController.class)
@Import(SecurityConfig.class)
class MissionControllerAuthorizationTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private MissionService missionService;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsServiceImpl userDetailsService;

    @Test
    @WithMockUser(roles = "REQUESTING_HOSPITAL")
    void requestingHospitalCannotMakeCentralHubDispatchDecision() throws Exception {
        mockMvc.perform(post("/api/missions/23/hub-decision")
                        .contentType("application/json")
                        .content("{\"action\":\"DISPATCH\",\"reason\":\"reviewed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPPLYING_HOSPITAL")
    void supplyingHospitalCannotMakeCentralHubDispatchDecision() throws Exception {
        mockMvc.perform(post("/api/missions/23/hub-decision")
                        .contentType("application/json")
                        .content("{\"action\":\"DISPATCH\",\"reason\":\"reviewed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCannotMakeCentralHubDispatchDecision() throws Exception {
        mockMvc.perform(post("/api/missions/23/hub-decision")
                        .contentType("application/json")
                        .content("{\"action\":\"DISPATCH\",\"reason\":\"reviewed\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "REQUESTING_HOSPITAL")
    void requestingHospitalCannotRunMissionRecoveryActions() throws Exception {
        mockMvc.perform(post("/api/missions/23/reassign").contentType("application/json").content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/missions/23/delay").contentType("application/json").content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/missions/23/abort").contentType("application/json").content("{\"reason\":\"test\"}"))
                .andExpect(status().isForbidden());
    }
}
