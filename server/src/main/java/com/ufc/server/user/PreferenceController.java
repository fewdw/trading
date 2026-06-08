package com.ufc.server.user;

import com.ufc.server.auth.CurrentUserService;
import com.ufc.server.dto.PreferenceDto;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The current user's UI preferences (e.g. dark mode). Auth required. */
@RestController
@RequestMapping("/api/preferences")
public class PreferenceController {

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    public PreferenceController(
        CurrentUserService currentUserService,
        UserRepository userRepository
    ) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public PreferenceDto get(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader
    ) {
        User user = currentUserService.requireUser(authHeader);
        return new PreferenceDto(user.isDarkMode());
    }

    @PutMapping
    @Transactional
    public PreferenceDto update(
        @RequestHeader(
            value = "Authorization",
            required = false
        ) String authHeader,
        @RequestBody PreferenceDto dto
    ) {
        User user = currentUserService.requireUser(authHeader);
        user.setDarkMode(dto.darkMode());
        userRepository.save(user);
        return new PreferenceDto(user.isDarkMode());
    }
}
