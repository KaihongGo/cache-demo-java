package com.example.cache.repo;

import com.example.cache.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) { 
        this.jdbc = jdbc; 
    }

    public Optional<User> findById(long id) {
        return jdbc.query("SELECT id, name, email FROM users WHERE id=?",
                rs -> rs.next()
                        ? Optional.of(new User(rs.getLong(1), rs.getString(2), rs.getString(3)))
                        : Optional.empty(),
                id);
    }

    public void update(User user) {
        jdbc.update("UPDATE users SET name=?, email=? WHERE id=?", user.name(), user.email(), user.id());
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM users WHERE id=?", id);
    }
}