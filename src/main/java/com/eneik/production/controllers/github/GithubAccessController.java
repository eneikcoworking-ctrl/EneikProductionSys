package com.eneik.production.controllers.github;

import com.eneik.production.services.github.GithubAccessService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class GithubAccessController {

    private final GithubAccessService githubAccessService;

    public GithubAccessController(GithubAccessService githubAccessService) {
        this.githubAccessService = githubAccessService;
    }

    @GetMapping("/projects/{id}/github-access")
    public GithubAccessService.GithubAccessResult getAccessStatus(@PathVariable UUID id) {
        return githubAccessService.getLatestResult(id);
    }

    @PostMapping("/projects/{id}/github-access/recheck")
    public GithubAccessService.GithubAccessResult recheckAccess(@PathVariable UUID id) {
        return githubAccessService.checkAccess(id);
    }

    @GetMapping("/github-access/defect-rate")
    public GithubAccessService.GithubSixSigmaDto getDefectRate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        return githubAccessService.calculateDefectRate(since);
    }
}
