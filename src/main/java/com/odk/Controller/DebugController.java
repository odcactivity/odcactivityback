package com.odk.Controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.HttpStatus;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Debug endpoint works");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testPost(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Debug POST endpoint works");
        response.put("receivedBody", body);
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    // Endpoint catch-all pour capturer toutes les requêtes
    @RequestMapping("/**")
    public ResponseEntity<Map<String, Object>> catchAll(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Endpoint not found");
        response.put("method", request.getMethod());
        response.put("url", request.getRequestURL().toString());
        response.put("path", request.getServletPath());
        response.put("query", request.getQueryString());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, 
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Method not supported");
        response.put("method", request.getMethod());
        response.put("url", request.getRequestURL().toString());
        response.put("supportedMethods", ex.getSupportedMethods());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(response);
    }
}
