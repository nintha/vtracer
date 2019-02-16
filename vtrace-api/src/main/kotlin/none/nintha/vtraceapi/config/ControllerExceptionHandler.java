package none.nintha.vtraceapi.config;

import none.nintha.vtraceapi.entity.Results;
import none.nintha.vtraceapi.entity.exceptions.UnloginException;
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
    public Results UnloginExceptionHandler(Exception ex, HttpServletRequest req) {
        return Results.unlogin();
    }
}