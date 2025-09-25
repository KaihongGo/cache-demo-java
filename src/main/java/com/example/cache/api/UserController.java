package com.example.cache.api;

import com.example.cache.core.UserCacheService;
import com.example.cache.model.User;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserCacheService svc;
    
    public UserController(UserCacheService svc) { 
        this.svc = svc; 
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> get(@PathVariable("id") long id) {
        Optional<User> u = svc.getById(id);
        return u.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> put(@PathVariable("id") long id,
                                    @RequestBody UserUpdateRequest req) {
        svc.update(id, new User(id, req.name(), req.email()));
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        svc.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record UserUpdateRequest(@NotBlank String name, @NotBlank String email) {}
}