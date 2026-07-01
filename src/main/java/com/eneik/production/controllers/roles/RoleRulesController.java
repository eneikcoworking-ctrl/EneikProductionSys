package com.eneik.production.controllers.roles;

import com.eneik.production.dto.RoleRules;
import com.eneik.production.services.RoleCapabilityLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
public class RoleRulesController {

    private final RoleCapabilityLoader roleCapabilityLoader;

    public RoleRulesController(RoleCapabilityLoader roleCapabilityLoader) {
        this.roleCapabilityLoader = roleCapabilityLoader;
    }

    @GetMapping("/{tag}/rules")
    public RoleRules getRules(@PathVariable String tag) {
        return roleCapabilityLoader.loadRules(tag);
    }
}
