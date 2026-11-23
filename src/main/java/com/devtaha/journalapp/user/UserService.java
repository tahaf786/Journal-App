package com.devtaha.journalapp.user;

import com.devtaha.journalapp.config.CacheConfig;
import com.devtaha.journalapp.entry.JournalEntryRepository;
import com.devtaha.journalapp.entry.PageResponse;
import com.devtaha.journalapp.event.ActivityStatsRepository;
import com.devtaha.journalapp.exception.BadRequestException;
import com.devtaha.journalapp.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final JournalEntryRepository entries;
    private final ActivityStatsRepository stats;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, JournalEntryRepository entries, ActivityStatsRepository stats,
                       PasswordEncoder encoder) {
        this.users = users;
        this.entries = entries;
        this.stats = stats;
        this.encoder = encoder;
    }

    public UserResponse get(String userId) {
        return UserResponse.from(find(userId));
    }

    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        User user = find(userId);
        user.setDisplayName(request.displayName());
        if (request.newPassword() != null) {
            if (request.currentPassword() == null
                    || !encoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new BadRequestException("Current password is incorrect");
            }
            user.setPasswordHash(encoder.encode(request.newPassword()));
        }
        return UserResponse.from(users.save(user));
    }

    /**
     * Deletes the user, their entries and their stats in one transaction, so a failure
     * part-way leaves nothing half deleted. Cached entries are dropped as well.
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.ENTRIES, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.ENTRY_PAGES, allEntries = true)})
    public void delete(String userId) {
        User user = find(userId);
        entries.deleteByUserId(userId);
        stats.deleteById(userId);
        users.delete(user);
    }

    public PageResponse<UserResponse> list(Pageable pageable) {
        return PageResponse.from(users.findAll(pageable), UserResponse::from);
    }

    public UserResponse changeAdminRole(String userId, boolean admin) {
        User user = find(userId);
        if (admin) {
            user.getRoles().add(Role.ADMIN);
        } else {
            user.getRoles().remove(Role.ADMIN);
        }
        return UserResponse.from(users.save(user));
    }

    private User find(String userId) {
        return users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
