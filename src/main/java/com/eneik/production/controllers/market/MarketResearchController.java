package com.eneik.production.controllers.market;

import com.eneik.production.services.market.MarketCorpusService;
import com.eneik.production.services.market.MarketResearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operator-facing control over the market corpus (see market-corpus/README.md).
 *
 * Research is created as a queued task rather than dispatched here on the spot: it then draws a Jules
 * session through the same account-capacity accounting as all other work. Bypassing that accounting is
 * precisely how a whole day's quota was burned on 2026-08-13.
 */
@RestController
@RequestMapping("/internal/market-corpus")
public class MarketResearchController {

    private final MarketResearchService marketResearchService;
    private final MarketCorpusService marketCorpusService;

    public MarketResearchController(MarketResearchService marketResearchService,
                                     MarketCorpusService marketCorpusService) {
        this.marketResearchService = marketResearchService;
        this.marketCorpusService = marketCorpusService;
    }

    /** What the corpus currently allows to influence decisions, and what it holds back as unverified. */
    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam(defaultValue = "DE") String market) {
        List<MarketCorpusService.Expectation> influential = marketCorpusService.influentialExpectations(market);
        return Map.of(
                "corpusAvailable", marketCorpusService.isAvailable(),
                "market", market,
                "influentialCount", influential.size(),
                "influential", influential
        );
    }

    /**
     * Queues one market-research task. Returns the task id so the operator can watch it through the normal
     * task/session views - there is no separate progress channel for this on purpose.
     */
    @PostMapping("/research")
    public Map<String, Object> research(@RequestParam String profileId,
                                        @RequestParam(defaultValue = "DE") String market,
                                        @RequestParam(defaultValue = "12") int sampleSize) {
        UUID taskId = marketResearchService.createResearchTask(profileId, market, sampleSize);
        return Map.of("taskId", taskId, "profileId", profileId, "market", market, "sampleSize", sampleSize);
    }
}
