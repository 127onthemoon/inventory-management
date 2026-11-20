package com.cumt.inventory.controller.web;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.dto.CategoryForm;
import com.cumt.inventory.entity.Category;
import com.cumt.inventory.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** 分类管理：查看对所有登录用户开放，增删改仅管理员 */
@Controller
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(Model model) {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Category c : categoryService.findAll()) {
            counts.put(c.getId(), categoryService.productCount(c.getId()));
        }
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("counts", counts);
        return "categories/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createForm(Model model) {
        model.addAttribute("form", new CategoryForm());
        return "categories/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", categoryService.toForm(categoryService.get(id)));
        return "categories/form";
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@Valid @ModelAttribute("form") CategoryForm form, BindingResult binding,
                       RedirectAttributes ra, Model model) {
        if (binding.hasErrors()) {
            return "categories/form";
        }
        try {
            categoryService.save(form);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "categories/form";
        }
        ra.addFlashAttribute("success", "分类已保存");
        return "redirect:/categories";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            categoryService.delete(id);
            ra.addFlashAttribute("success", "分类已删除");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/categories";
    }
}
