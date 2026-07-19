package com.eneik.production.services;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.repositories.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoleCapabilityLoaderTest {

    private RoleRepository roleRepository;
    private RoleRulesParser roleRulesParser;
    private RoleCapabilityLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        roleRulesParser = new RoleRulesParser();
        loader = new RoleCapabilityLoader(roleRepository, roleRulesParser);
    }

    @Test
    void loadRules_CacheInvalidation() throws IOException, InterruptedException {
        String tag = "TAG-01";
        Path rulesFile = tempDir.resolve("rules.md");
        Files.writeString(rulesFile, "# Scope\nInitial rules");

        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        role.setRulesPath(rulesFile.toString());

        when(roleRepository.findById(tag)).thenReturn(Optional.of(role));

        RoleRules rules1 = loader.loadRules(tag);
        assertEquals("# Scope\nInitial rules", rules1.scope());

        // Update file
        Thread.sleep(1100); // Ensure mtime changes (file systems often have 1s resolution)
        Files.writeString(rulesFile, "# Scope\nUpdated rules");

        RoleRules rules2 = loader.loadRules(tag);
        assertEquals("# Scope\nUpdated rules", rules2.scope());
        assertNotSame(rules1, rules2);
    }

    @Test
    void loadRules_UsesCache() throws IOException {
        String tag = "TAG-01";
        Path rulesFile = tempDir.resolve("rules.md");
        Files.writeString(rulesFile, "# Scope\nConstant rules");

        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        role.setRulesPath(rulesFile.toString());

        when(roleRepository.findById(tag)).thenReturn(Optional.of(role));

        RoleRules rules1 = loader.loadRules(tag);
        RoleRules rules2 = loader.loadRules(tag);

        assertSame(rules1, rules2);
    }

    @Test
    void loadRules_NotFound() {
        when(roleRepository.findById("UNKNOWN")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> loader.loadRules("UNKNOWN"));
    }

    @Test
    void loadRawCharter_ReturnsFullFileContentVerbatim() throws IOException {
        String tag = "TAG-01";
        Path rulesFile = tempDir.resolve("rules.md");
        String fullCharter = "# BARCAN-TAG-01\n## ФИЛОСОФСКИЙ ФУНДАМЕНТ\n| 1 | **Someone** | Principle | Application |\n";
        Files.writeString(rulesFile, fullCharter);

        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        role.setRulesPath(rulesFile.toString());

        when(roleRepository.findById(tag)).thenReturn(Optional.of(role));

        assertEquals(fullCharter, loader.loadRawCharter(tag));
    }
}
