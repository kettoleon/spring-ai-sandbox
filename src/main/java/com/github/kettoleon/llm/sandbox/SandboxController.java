package com.github.kettoleon.llm.sandbox;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import static com.github.kettoleon.llm.sandbox.common.configuration.GlobalTemplateVariables.page;

@Controller
public class SandboxController {

    @GetMapping(path = {"", "/"})
    public ModelAndView getChats() {
        return page("home", "Proof of concepts", "Spring AI Sandbox");
    }


}
