package com.revpay.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoutingController {

    @GetMapping("/cards")
    public String redirectCards() {
        return "redirect:/wallet/cards";
    }

    @GetMapping("/send")
    public String redirectSend() {
        return "redirect:/transactions/send";
    }

    @GetMapping("/request")
    public String redirectRequest() {
        return "redirect:/transactions/request";
    }

}
