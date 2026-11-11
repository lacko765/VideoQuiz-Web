package edu.videoquiz;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.*;
import java.io.IOException;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",Objects.toString(e.getMessage(),"Invalid input."))); }
    @ExceptionHandler(NoSuchElementException.class) ResponseEntity<?> missing() { return ResponseEntity.status(404).body(Map.of("message","Item not found.")); }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<?> malformed() { return ResponseEntity.badRequest().body(Map.of("message","The request contains invalid or missing fields.")); }
    @ExceptionHandler(IOException.class) ResponseEntity<?> storage() { return ResponseEntity.status(503).body(Map.of("message","The server could not read or save data. Your previously saved upload chunks remain available; retry shortly.")); }
    @ExceptionHandler(Exception.class) ResponseEntity<?> unexpected() { return ResponseEntity.status(500).body(Map.of("message","The request could not be completed. Refresh and retry; contact the administrator if it persists.")); }
}
