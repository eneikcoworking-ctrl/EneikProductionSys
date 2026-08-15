package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One link of one declared value chain, actually walked on the deployed instance.
 *
 * Closes the half of F30 that no amount of building can close: every valuePath in the market corpus states
 * what must be POSSIBLE, and a possibility claim is not witnessed by another possibility claim. Until this
 * existed the factory reached DELIVERED on merge counts - a claim about what was built standing in for a
 * claim about what was shown.
 *
 * Append-only, exactly like {@link ClientRuntimeObservationEntity}. A traversal is an event that happened at
 * a moment; a product that changes afterwards does not un-happen it, it makes the traversal describe a
 * product that no longer exists. Noticing that is the referent test's job (see RuntimeVerdictLayer), not
 * this row's - a row that edited itself when the world changed would destroy the evidence needed to see
 * that the world changed.
 */
@Entity
@Table(name = "client_acceptance_traversals")
public class ClientAcceptanceTraversalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** Which declared chain, by corpus id rather than by position - profiles gain and reorder paths. */
    @Column(name = "profile_id", nullable = false, length = 64)
    private String profileId;

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    /** The link's own text, verbatim from the corpus path, for the same reason as profileId. */
    @Column(name = "link", nullable = false, length = 512)
    private String link;

    @Column(name = "traversed_at", nullable = false)
    private Instant traversedAt = Instant.now();

    /**
     * The acceptance rule requires the CLIENT to have walked it. A factory-side walk witnesses a different
     * proposition - that the path CAN be walked - and conflating the two would let the factory accept its
     * own work, which is the exact substitution this whole repair exists to stop.
     */
    @Column(name = "walked_by", nullable = false, length = 32)
    private String walkedBy;

    @Column(name = "evidence", length = 2000)
    private String evidence;

    @Column(name = "instance_url", length = 512)
    private String instanceUrl;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getProfileId() { return profileId; }
    public void setProfileId(String profileId) { this.profileId = profileId; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public Instant getTraversedAt() { return traversedAt; }
    public void setTraversedAt(Instant traversedAt) { this.traversedAt = traversedAt; }

    public String getWalkedBy() { return walkedBy; }
    public void setWalkedBy(String walkedBy) { this.walkedBy = walkedBy; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public String getInstanceUrl() { return instanceUrl; }
    public void setInstanceUrl(String instanceUrl) { this.instanceUrl = instanceUrl; }
}
