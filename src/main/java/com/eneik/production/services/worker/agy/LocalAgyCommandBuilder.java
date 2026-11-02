package com.eneik.production.services.worker.agy;

import com.eneik.production.services.worker.security.AgyWorkerSecurityGuard;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Command builder for invoking the local agy CLI within sovereign execution pools.
 * Enforces Raz Deontic Gate (D006) on the execution user and routes model by Cynefin domain.
 */
@Component
public class LocalAgyCommandBuilder {

    public static final String DEFAULT_FLASH_MODEL = "gemini-3.8-flash-medium";
    public static final String DEFAULT_PRO_MODEL = "gemini-3.8-pro";
    public static final String DEFAULT_REMOTECLI3_BIN = "/home/remotecli3/.local/bin/agy";
    public static final String DEFAULT_ROOT_BIN = "/usr/local/bin/agy";
    public static final int DEFAULT_TIMEOUT_SECONDS = 180;

    /**
     * Resolves the appropriate model name according to Dave Snowden's Cynefin framework:
     * - 'complex' -> gemini-3.8-pro
     * - 'clear', 'simple', 'complicated', or default -> gemini-3.8-flash-medium
     */
    public String resolveModel(String cynefinDomain) {
        if ("complex".equalsIgnoreCase(cynefinDomain)) {
            return DEFAULT_PRO_MODEL;
        }
        return DEFAULT_FLASH_MODEL;
    }

    /**
     * Resolves the default binary path for a given user profile.
     */
    public String resolveBinaryPath(String executionUser) {
        if ("root".equalsIgnoreCase(executionUser)) {
            return DEFAULT_ROOT_BIN;
        }
        return DEFAULT_REMOTECLI3_BIN;
    }

    /**
     * Builds the process argument list for executing agy CLI.
     * Enforces Raz Deontic Gate: throws ExclusionaryReasonViolationException if executionUser is forbidden.
     */
    public List<String> buildCommand(String executionUser, String binaryPath, String model, String prompt) {
        AgyWorkerSecurityGuard.validateWorkerExecutionUser(executionUser);

        List<String> command = new ArrayList<>();
        String bin = (binaryPath != null && !binaryPath.isBlank()) ? binaryPath : resolveBinaryPath(executionUser);
        String targetModel = (model != null && !model.isBlank()) ? model : DEFAULT_FLASH_MODEL;

        if ("root".equalsIgnoreCase(executionUser)) {
            command.add(bin);
        } else {
            // For remotecli3 or other unprivileged users, run via sudo -n -u <user>
            command.add("sudo");
            command.add("-n");
            command.add("-u");
            command.add(executionUser);
            command.add(bin);
        }

        command.add("--model");
        command.add(targetModel);
        command.add("--dangerously-skip-permissions");
        command.add("-p");
        command.add(prompt != null ? prompt : "");

        return command;
    }

    /**
     * Overload taking only executionUser, cynefinDomain, and prompt.
     */
    public List<String> buildCommand(String executionUser, String cynefinDomain, String prompt) {
        String model = resolveModel(cynefinDomain);
        String bin = resolveBinaryPath(executionUser);
        return buildCommand(executionUser, bin, model, prompt);
    }
}
