package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.dashboard.ClientDeliveryDto;
import com.eneik.production.services.dashboard.ClientDeliveryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/projects/{projectId}/client-delivery")
public class ClientDeliveryController {

    private final ClientDeliveryService clientDeliveryService;

    public ClientDeliveryController(ClientDeliveryService clientDeliveryService) {
        this.clientDeliveryService = clientDeliveryService;
    }

    @GetMapping
    public ClientDeliveryDto getDelivery(@PathVariable UUID projectId) {
        return clientDeliveryService.getDelivery(projectId);
    }
}
