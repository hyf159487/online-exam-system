import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenHash {
    public static void main(String[] args) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        String pwd = args.length > 0 ? args[0] : "admin123";
        String hash = enc.encode(pwd);
        System.out.println("原文: " + pwd);
        System.out.println("BCrypt: " + hash);
        System.out.println("自验: " + enc.matches(pwd, hash));
    }
}
