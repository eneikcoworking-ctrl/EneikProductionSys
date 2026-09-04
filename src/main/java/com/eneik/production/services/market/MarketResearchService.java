package com.eneik.production.services.market;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TargetContext;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Turns unverified corpus entries into measured ones by having a Jules session look at products that
 * actually exist and ship, then commit what it found into market-corpus/ as a file.
 *
 * Why a session rather than asking a model: an answer in a chat window is testimony, and this system
 * everywhere else insists on evidence. A session produces a commit - a diff a human can read, disagree
 * with, and correct - and it states which products it examined, so a claim like "most shops in this
 * segment offer guest checkout" stops being an opinion and becomes a share with a sample behind it.
 *
 * Runs against the factory's OWN repository ({@link TargetContext#ORCHESTRATOR_SYSTEM}), never a client's:
 * market knowledge belongs to the factory, and nothing here may touch a client codebase. That target
 * context existed in JulesDispatchService but nothing ever set it - this is its first real user.
 */
@Service
public class MarketResearchService {
    private static final Logger log = LoggerFactory.getLogger(MarketResearchService.class);

    /** Delivery/decision role - this produces a written finding, not product code. */
    private static final String RESEARCH_ROLE = "BARCAN-TAG-09";

    private final TaskRepository taskRepository;
    private final RoleRepository roleRepository;
    private final ProjectRepository projectRepository;

    public MarketResearchService(TaskRepository taskRepository, RoleRepository roleRepository,
                                  ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.roleRepository = roleRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * Creates one research task. Deliberately does NOT dispatch it: dispatch goes through the normal
     * queued-task path so the session is drawn from the same account pool with the same capacity
     * accounting as everything else. Bypassing that accounting is exactly how a day's Jules quota got
     * burned on 2026-08-13.
     *
     * @param profileId which product profile to study, e.g. "shop" or "booking"
     * @param market    "DE" or "US" - findings are only comparable within one market
     * @param sampleSize how many real products to examine; the resulting share is meaningless without it
     */
    @Transactional
    public UUID createResearchTask(String profileId, String market, int sampleSize) {
        RoleEntity role = roleRepository.findById(RESEARCH_ROLE).orElse(null);
        if (role == null) {
            throw new IllegalStateException("Role " + RESEARCH_ROLE + " not found; cannot create research task");
        }
        // A TaskEntity requires a project, but ORCHESTRATOR_SYSTEM makes the dispatcher resolve the
        // factory's own repository instead of this project's - so the carrier project is bookkeeping only
        // and its code is never touched. Picking the most recent one keeps the task visible somewhere real
        // rather than inventing a synthetic project row.
        ProjectEntity carrier = projectRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No project exists to carry the research task"));

        int boundedSample = Math.max(5, Math.min(sampleSize, 40));

        TaskEntity task = new TaskEntity();
        task.setProject(carrier);
        task.setRole(role);
        task.setTargetContext(TargetContext.ORCHESTRATOR_SYSTEM);
        task.initializeStatus(TaskStatus.queued);
        task.setTitle("Market research " + profileId + " " + market);
        task.setDescription(researchPrompt(profileId, market, boundedSample));
        task.setAcceptanceCriteria("Given the market and sample size this task names, When the research ends, Then it reports counts observed in that many real products with the date and the method used, and marks any figure it could not source as absent rather than estimating it.");
        task = taskRepository.save(task);

        log.info("MarketResearchService: created research task {} for profile={} market={} sample={}",
                task.getId(), profileId, market, boundedSample);
        return task.getId();
    }

    String researchPrompt(String profileId, String market, int sampleSize) {
        return """
                You are researching what products of one kind actually contain in one market, so that a
                decomposition system stops guessing. Produce a measurement, not an opinion.

                Product kind: %s
                Market: %s
                Sample size: examine %d real, currently-operating products of this kind serving this market.

                METHOD - follow it exactly, because the number is worthless if the method is not stated:
                1. Choose %d real products that are genuinely live and serving customers in %s right now.
                   Prefer ordinary working businesses over famous flagship brands - the flagship is not what
                   a normal client's competitor looks like. Write down every product's URL.
                2. Visit each one with Playwright (already available in this environment) and observe what
                   is actually there. Do not reason about what such products "usually" have - look.
                3. For each capability listed below, count in how many of the %d you could actually observe
                   it. If you could not determine it for a product, count that product as "unknown" and say
                   so; never guess to complete a row.

                Capabilities to count:
                  - guest checkout available without creating an account (where purchasing exists)
                  - prices shown including tax
                  - an imprint / legal-entity disclosure page reachable from the footer
                  - a cookie or tracking consent prompt on first visit
                  - a visible order/booking confirmation step before the action becomes final
                  - a way to cancel or change what was ordered/booked after the fact
                  - reminder or status notifications by email/SMS
                  - a stated returns or cancellation policy
                  - the interface being usable at 375px width (mobile)
                  - a visible way to contact a human

                HONESTY REQUIREMENTS - these matter more than completeness:
                  - Report what you observed, never what you expect. A capability you could not verify is
                    "unknown", not "absent".
                  - If you could only examine fewer products than asked (blocked, unreachable, region-locked),
                    report the smaller number you really examined. A share over 6 real products is useful; a
                    share over 20 imagined ones is worse than nothing.
                  - Never invent a URL. Every product you count must be one you actually opened.

                Deliverable: create a branch and open a PR containing ONLY this one new file,
                `market-corpus/observations/%s-%s.json`, and no other changes:
                {"profileId": "%s", "market": "%s", "observedAt": "YYYY-MM-DD",
                 "method": "one paragraph describing exactly how you selected and examined the products",
                 "productsExamined": ["https://...", "https://..."],
                 "counts": [{"capability": "guest-checkout", "present": 0, "absent": 0, "unknown": 0}],
                 "notes": "anything that would change how these numbers should be read"}

                Do not modify any other file. Do not write product code.
                """.formatted(profileId, market, sampleSize, sampleSize, market, sampleSize,
                profileId, market, profileId, market);
    }
}
