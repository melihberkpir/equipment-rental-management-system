package com.equipmentrental.auth;

import com.equipmentrental.auth.dto.AuthResponse;
import com.equipmentrental.auth.dto.LoginRequest;
import com.equipmentrental.auth.dto.RegisterRequest;
import com.equipmentrental.auth.dto.UserResponse;
import com.equipmentrental.domain.Role;
import com.equipmentrental.domain.RoleName;
import com.equipmentrental.domain.User;
import com.equipmentrental.exception.BadRequestException;
import com.equipmentrental.exception.ConflictException;
import com.equipmentrental.repository.RoleRepository;
import com.equipmentrental.repository.UserRepository;
import com.equipmentrental.security.AuthenticatedUser;
import com.equipmentrental.security.JwtService;
import java.nio.charset.StandardCharsets;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Public registration always creates a CUSTOMER account. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BadRequestException("Password must not exceed 72 bytes");
        }
        String email = EmailNormalizer.normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Role customerRole = roles.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is missing"));
        User user = users.saveAndFlush(new User(customerRole, request.fullName().strip(), email,
                passwordEncoder.encode(request.password())));
        return toResponse(new AuthenticatedUser(user));
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        EmailNormalizer.normalize(request.email()), request.password()));
        return toResponse((AuthenticatedUser) authentication.getPrincipal());
    }

    private AuthResponse toResponse(AuthenticatedUser user) {
        return new AuthResponse(jwtService.generateToken(user.getUsername()), "Bearer",
                jwtService.expirationSeconds(),
                new UserResponse(user.getId(), user.getFullName(), user.getUsername(), user.getRole()));
    }
}
