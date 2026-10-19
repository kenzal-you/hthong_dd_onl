package face_recognition.config;

import face_recognition.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import face_recognition.repository.UserRepository;

@Configuration
@RequiredArgsConstructor
public class AdminInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initAdmin() {
        return args -> {
            if (!userRepository.existsByUsername("admin")) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("1"));
                admin.setRole(User.Role.ADMIN);
                userRepository.save(admin);
                System.out.println("Đã tạo thành công tk admin - 1");
            } else {
                System.out.println("Tk admin đã tồn tại, bỏ qua việc tạo mới!");
            }
        };
    }
}