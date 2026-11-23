package com.devtaha.journalapp.user;

import com.devtaha.journalapp.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/** Creates the first administrator when ADMIN_USERNAME and ADMIN_PASSWORD are set and the user does not exist. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties props;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminBootstrap(AppProperties props, UserRepository users, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = props.admin().username();
        String password = props.admin().password();
        if (username.isBlank() || password.isBlank() || users.existsByUsername(username)) {
            return;
        }
        User admin = new User(username, username, encoder.encode(password));
        admin.setRoles(EnumSet.of(Role.USER, Role.ADMIN));
        users.save(admin);
        log.info("Created initial administrator '{}'", username);
    }
}
