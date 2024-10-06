package com.github.kettoleon.llm.sandbox.common.configuration;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import static com.github.kettoleon.llm.sandbox.common.configuration.GlobalTemplateVariables.page;


@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

    @RequestMapping("/error")
    public ModelAndView handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (status != null) {
            Integer statusCode = Integer.valueOf(status.toString());
            return page("error", "Error")
                    .addObject("statusCode", statusCode);
        }
        return page("error", "Oops! This is so embarrassing...");
    }

}