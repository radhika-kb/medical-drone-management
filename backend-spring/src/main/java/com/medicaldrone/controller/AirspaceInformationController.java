package com.medicaldrone.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/airspace-information")
public class AirspaceInformationController {
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CENTRAL_HUB', 'REQUESTING_HOSPITAL', 'SUPPLYING_HOSPITAL')")
    public List<Map<String, Object>> list() {
        return List.of(
                Map.of("name", "Kempegowda International Airport CTR", "classification", "OTHER",
                        "classificationDetail", "ATS Class D", "sourceAuthority", "Airports Authority of India",
                        "source", "https://aim-india.aai.aero/eaip/eaip-v2-05-2026/eAIP/IN-AD%202.1VOBL-en-GB.html",
                        "verticalLimit", "FL95", "geometryStatus", "NOT_RENDERED_RESTRICTED_AREAS_EXCLUDED",
                        "boundaryDescription", "AAI-published CTR boundary includes an arc and straight segments, except VOR 185A, VOR 185B and VOR 185C; exclusions are not geometrically resolved here.",
                        "lastVerified", "2026-06-11", "currentStatus", "AIP reference; latest cycle not verified"),
                Map.of("name", "HAL Airport controlled/restricted airspace references", "classification", "OTHER",
                        "classificationDetail", "HAL ATC controlled VOR 181A, VOR 181B and VOR 185; CTR not established",
                        "sourceAuthority", "Airports Authority of India",
                        "source", "https://aim-india.aai.aero/eaip/eaip-v2-01-2026/eAIP/IN-AD%202.1VOBG-en-GB.html",
                        "geometryStatus", "NOT_PUBLISHED_IN_AD_2_17", "boundaryDescription", "AAI VOBG AD 2.17 does not establish a CTR and provides no coordinates for the listed VOR restricted airspaces.",
                        "lastVerified", "2026-01-22", "currentStatus", "AIP reference; latest cycle not verified")
        );
    }
}
