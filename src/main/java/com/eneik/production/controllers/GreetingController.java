package com.eneik.production.controllers;

import com.eneik.production.models.domain.Greeting;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * @file GreetingController.java
 * @agent TAG-02 (Rigid Designator)
 * @description Controller for the Hello World greeting, providing a fixed designator.
 */
@RestController
@RequestMapping("/api")
public class GreetingController {

    @GetMapping("/hello")
    public Greeting getHello() {
        // Rigidly designated ID and Message
        UUID fixedId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String message = "Hello World: The Agency is Operational.";

        return new Greeting(fixedId, message);
    }
}
