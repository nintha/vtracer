package none.nintha.vtracer.controller;

import none.nintha.vtracer.util.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HttpErrorController {
    @GetMapping("/401")
    public Result unlogin(){
        return Result.unlogin();
    }
}
