package com.eneik.production.models.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class RoleEntity {
    @Id
    private String tag;

    private String description;

    @Column(nullable = false)
    private String rulesPath;

    @Column(nullable = false)
    private boolean active = true;

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRulesPath() { return rulesPath; }
    public void setRulesPath(String rulesPath) { this.rulesPath = rulesPath; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
