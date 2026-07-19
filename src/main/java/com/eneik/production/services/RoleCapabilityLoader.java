package com.eneik.production.services;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.repositories.RoleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoleCapabilityLoader {

    private final RoleRepository roleRepository;
    private final RoleRulesParser roleRulesParser;
    private final Map<String, CachedRules> cache = new ConcurrentHashMap<>();

    public RoleCapabilityLoader(RoleRepository roleRepository, RoleRulesParser roleRulesParser) {
        this.roleRepository = roleRepository;
        this.roleRulesParser = roleRulesParser;
    }

    public RoleRules loadRules(String roleTag) {
        return loadCached(roleTag).rules();
    }

    // The parsed RoleRules fields are lossy (charter files use three different Markdown conventions for
    // their deontic sections - see RoleRulesParser), and the philosophy table isn't parsed into any field
    // at all. Jules is meant to actually inhabit the role's full worldview, not a keyword-extracted
    // summary of it, so dispatch reads the raw charter text directly instead of relying on this parser.
    public String loadRawCharter(String roleTag) {
        return loadCached(roleTag).rawMarkdown();
    }

    private CachedRules loadCached(String roleTag) {
        RoleEntity role = roleRepository.findById(roleTag)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found: " + roleTag));

        Path path = Paths.get(role.getRulesPath());
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rules file not found at " + role.getRulesPath());
        }

        try {
            Instant mtime = Files.getLastModifiedTime(path).toInstant();
            CachedRules cached = cache.get(roleTag);

            if (cached != null && cached.mtime.equals(mtime)) {
                return cached;
            }

            String markdown = Files.readString(path);
            RoleRules parsed = roleRulesParser.parse(roleTag, markdown);
            CachedRules fresh = new CachedRules(parsed, markdown, mtime);
            cache.put(roleTag, fresh);
            return fresh;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading rules file", e);
        }
    }

    private record CachedRules(RoleRules rules, String rawMarkdown, Instant mtime) {}
}
