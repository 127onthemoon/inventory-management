package com.cumt.inventory.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 登录页与无权限页 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @RequestMapping("/403")
    public String forbidden() {
        return "error/403";
    }
}
