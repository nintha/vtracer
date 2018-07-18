package none.nintha.vtracer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
//@EnableScheduling
@SpringBootApplication
public class VtracerApplication {

	public static void main(String[] args) {
		SpringApplication.run(VtracerApplication.class, args);
	}

}
