package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RootController {

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of("message", "LMS Student API is running", "version", "1.0");
    }
}
