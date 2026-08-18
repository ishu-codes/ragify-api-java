package ragify.controllers;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of(
            "name",
            "ragify",
            "version",
            "0.1",
            "project",
            "https://github.com/ishu-codes/ragify",
            "author",
            "Ishu Kumar"
        );
    }
}
