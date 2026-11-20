package com.cumt.inventory.controller.web;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.dto.UserForm;
import com.cumt.inventory.entity.Role;
import com.cumt.inventory.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 用户管理：URL 级（SecurityConfig）+ 类级 @PreAuthorize 双重限制为管理员 */
@Controller
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        return "users/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new UserForm());
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", userService.toForm(userService.get(id)));
        model.addAttribute("roles", Role.values());
        return "users/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") UserForm form, BindingResult binding,
                       Authentication auth, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("roles", Role.values());
            return "users/form";
        }
        try {
            userService.save(form, auth.getName());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("roles", Role.values());
            return "users/form";
        }
        ra.addFlashAttribute("success", "用户已保存");
        return "redirect:/users";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            userService.toggleEnabled(id, auth.getName());
            ra.addFlashAttribute("success", "用户状态已更新");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }
}
