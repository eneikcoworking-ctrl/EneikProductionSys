package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "jules_activity_responses",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_jules_activity_response_session_hash",
                columnNames = {"jules_session_id", "activity_hash"}
        )
)
public class JulesActivityResponseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "jules_session_id", nullable = false)
    private UUID julesSessionId;

    @Column(name = "activity_name", nullable = false, length = 256)
    private String activityName;

    @Column(name = "activity_hash", nullable = false, length = 64)
    private String activityHash;

    @Lob
    @Column(name = "question", nullable = false)
    private String question;

    @Lob
    @Column(name = "response")
    private String response;

    @Column(name = "sent", nullable = false)
    private boolean sent = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getJulesSessionId() { return julesSessionId; }
    public void setJulesSessionId(UUID julesSessionId) { this.julesSessionId = julesSessionId; }

    public String getActivityName() { return activityName; }
    public void setActivityName(String activityName) { this.activityName = activityName; }

    public String getActivityHash() { return activityHash; }
    public void setActivityHash(String activityHash) { this.activityHash = activityHash; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
}
