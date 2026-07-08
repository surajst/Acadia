package com.schoolos.language;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile")
public class LanguageController {

    @GetMapping("/languages")
    public Object listLanguages() {
        return SupportedLanguages.asList();
    }
}
