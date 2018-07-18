package none.nintha.vtracer.controller;

import none.nintha.vtracer.util.Result;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1")
public class LoginController {
    @Autowired
    HttpSession session;
    @Value("${default.username}")
    String defaultUsername;
    @Value("${default.password}")
    String defaultPassword;

    @GetMapping("/login")
    public Result login(String username, String password) {
        if(Strings.isBlank(username) || Strings.isBlank(password)){
            return Result.failed("error username or password");
        }

        if (Objects.equals(username, defaultUsername) && Objects.equals(password, defaultPassword)) {
            logout();
            session.setAttribute("user", username);
            session.setMaxInactiveInterval(3600 * 24 * 7); // 过期时间1周
            return Result.success();
        }
        return Result.failed("error username or password");
    }

    @GetMapping("/logout")
    public Result logout() {
        session.removeAttribute("user");
        session.invalidate();
        return Result.success();
    }

    @GetMapping("/userInfo")
    public Result userInfo() {
        String user = (String) session.getAttribute("user");
        return Result.success().put("user", user);
    }

}
