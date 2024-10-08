package backend.ecommerce.ecommerceapi.service.user;

import backend.ecommerce.ecommerceapi.config.exception.BadCredentialsException;
import backend.ecommerce.ecommerceapi.config.exception.DuplicateEmailException;
import backend.ecommerce.ecommerceapi.config.exception.PasswordDoesNotMatchException;
import backend.ecommerce.ecommerceapi.config.security.jwt.JwtUtils;
import backend.ecommerce.ecommerceapi.dto.user.UserAuthResponseDto;
import backend.ecommerce.ecommerceapi.dto.user.UserLoginDto;
import backend.ecommerce.ecommerceapi.dto.user.UserRegisterDto;
import backend.ecommerce.ecommerceapi.entity.ERole;
import backend.ecommerce.ecommerceapi.entity.Role;
import backend.ecommerce.ecommerceapi.entity.User;
import backend.ecommerce.ecommerceapi.repository.RoleRepository;
import backend.ecommerce.ecommerceapi.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtils jwtUtils,
                           AuthenticationManager authenticationManager, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.authenticationManager = authenticationManager;
        this.roleRepository = roleRepository;
    }

    @Override
    public Boolean passwordMatch(String password, String confirmPassword) {
        return password.equals(confirmPassword);
    }

    @Override
    public Boolean existsByEmail(String email) {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getEmail().equals(email)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void hashPassword(UserRegisterDto userRegisterDto) {
        String hashedPassword = passwordEncoder.encode(userRegisterDto.getPassword());
        userRegisterDto.setPassword(hashedPassword);
    }

    @Override
    public Boolean validateUserInfo(UserRegisterDto userRegisterDto) {
        if (passwordMatch(userRegisterDto.getPassword(),
                userRegisterDto.getConfirmPassword()).equals(false)) {
           throw new PasswordDoesNotMatchException("Password does not match");
        }
        if (existsByEmail(userRegisterDto.getEmail()).equals(true)) {
            throw new DuplicateEmailException("Email already exists");
        } else {
            return true;
        }
    }

    public static UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority(role.getRoleName().toString()))
                        .toList())
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    @Override
    public UserAuthResponseDto register(User user) {
        Role userRole = roleRepository.findByRoleName(ERole.USER).orElseThrow(
                () -> new RuntimeException("Role is not found."));

        if (user.getRoles() == null) {
            user.setRoles(new HashSet<>());
        }
        user.getRoles().add(userRole);

        String token = jwtUtils.getToken(toUserDetails(user));


        this.userRepository.save(user);
        return UserAuthResponseDto.builder()
                .token(token)
                .build();
    }

    @Override
    public UserAuthResponseDto login(UserLoginDto userLoginDto) {
        try {
            doAuthenticate(userLoginDto.getEmail(), userLoginDto.getPassword());

            User userDetails = userRepository.findByEmail(userLoginDto.getEmail()).orElseThrow(
                    () -> new RuntimeException("Error: User is not found."));

            String jwtToken = jwtUtils.getToken(toUserDetails(userDetails));

            return UserAuthResponseDto.builder()
                    .token(jwtToken)
                    .build();

        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid Credentials");
        }

    }

    private void doAuthenticate(String email, String password) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email, password);
        try {
            authenticationManager.authenticate(authentication);
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Invalid Credentials");
        }
    }

}
