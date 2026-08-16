package com.eneik.production.controllers.settings;

import com.eneik.production.dto.JulesConfigDto;
import com.eneik.production.models.persistence.JulesConfigEntity;
import com.eneik.production.repositories.JulesConfigRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/jules-configs")
public class JulesConfigController {
    private final JulesConfigRepository julesConfigRepository;

    public JulesConfigController(JulesConfigRepository julesConfigRepository) {
        this.julesConfigRepository = julesConfigRepository;
    }

    @GetMapping
    public List<JulesConfigDto> list() {
        return julesConfigRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    public JulesConfigDto create(@RequestBody Map<String, Object> payload) {
        JulesConfigEntity entity = new JulesConfigEntity();
        entity.setName((String) payload.get("name"));
        entity.setApiKey((String) payload.get("apiKey"));
        entity.setEnabled(payload.get("enabled") == null || (boolean) payload.get("enabled"));
        return toDto(julesConfigRepository.save(entity));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JulesConfigDto> update(@PathVariable UUID id, @RequestBody Map<String, Object> payload) {
        return julesConfigRepository.findById(id)
                .map(entity -> {
                    if (payload.containsKey("name")) entity.setName((String) payload.get("name"));
                    if (payload.containsKey("apiKey") && !((String) payload.get("apiKey")).startsWith("****")) {
                        entity.setApiKey((String) payload.get("apiKey"));
                    }
                    if (payload.containsKey("enabled")) entity.setEnabled((boolean) payload.get("enabled"));
                    return ResponseEntity.ok(toDto(julesConfigRepository.save(entity)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (julesConfigRepository.existsById(id)) {
            julesConfigRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private JulesConfigDto toDto(JulesConfigEntity entity) {
        String masked = entity.getApiKey();
        if (masked != null && masked.length() > 4) {
            masked = "****" + masked.substring(masked.length() - 4);
        } else {
            masked = "****";
        }
        return new JulesConfigDto(entity.getId(), entity.getName(), masked, entity.isEnabled());
    }
}
