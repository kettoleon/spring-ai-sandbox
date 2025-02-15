package com.github.kettoleon.llm.sandbox.common.configuration;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;

@ControllerAdvice(annotations = Controller.class)
public class GlobalTemplateVariables {

    public static ModelAndView page(String viewId, String title, String projectTitle) {
        ModelAndView modelAndView = new ModelAndView("index");
        modelAndView.addObject("page", viewId);
        modelAndView.addObject("pageTitle", title);
        modelAndView.addObject("projectTitle", projectTitle);
        return modelAndView;
    }

    @ModelAttribute("version")
    public String version() {
        return Optional.ofNullable(getClass().getPackage().getImplementationVersion()).orElse("0.0.x-SNAPSHOT");
    }

}
