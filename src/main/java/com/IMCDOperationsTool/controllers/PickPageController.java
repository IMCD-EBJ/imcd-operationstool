package com.IMCDOperationsTool.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Serves the pick details page. The number in the path is the IMCD pick.
 */
@Controller
public class PickPageController {

    @GetMapping("/pick/{pick}/details")
    public String details(@PathVariable int pick) {
        return "forward:/pick.html";
    }
}
