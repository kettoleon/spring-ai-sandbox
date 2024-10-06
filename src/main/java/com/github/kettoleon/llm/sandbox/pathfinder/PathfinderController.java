package com.github.kettoleon.llm.sandbox.pathfinder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import static com.github.kettoleon.llm.sandbox.common.configuration.GlobalTemplateVariables.page;

@Controller
@Slf4j
public class PathfinderController {


    @GetMapping(path = {"", "/", "/cortex"})
    public ModelAndView chats() {
        ModelAndView cortex = page("cortex/cortex", "Cortex");
        return cortex;
    }



}
