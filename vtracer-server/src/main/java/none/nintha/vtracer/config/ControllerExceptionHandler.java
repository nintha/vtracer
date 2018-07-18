package none.nintha.vtracer.config;

import none.nintha.vtracer.util.Result;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
@ResponseBody
public class ControllerExceptionHandler {
    /**
     * 处理未登录异常
     * @return
     */
    @ExceptionHandler({ UnloginException.class })
    public Result MethodArgumentNotValidHandler(Exception ex, HttpServletRequest req) {
        return Result.unlogin();
    }
}