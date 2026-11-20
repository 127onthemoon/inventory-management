package com.cumt.inventory.controller.web;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.dto.SupplierForm;
import com.cumt.inventory.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 供应商管理 */
@Controller
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", supplierService.search(keyword, PageUtils.of(page, 10)));
        model.addAttribute("keyword", keyword);
        return "suppliers/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createForm(Model model) {
        model.addAttribute("form", new SupplierForm());
        return "suppliers/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", supplierService.toForm(supplierService.get(id)));
        return "suppliers/form";
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@Valid @ModelAttribute("form") SupplierForm form, BindingResult binding,
                       RedirectAttributes ra, Model model) {
        if (binding.hasErrors()) {
            return "suppliers/form";
        }
        try {
            supplierService.save(form);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "suppliers/form";
        }
        ra.addFlashAttribute("success", "供应商已保存");
        return "redirect:/suppliers";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            supplierService.delete(id);
            ra.addFlashAttribute("success", "供应商已删除");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/suppliers";
    }
}
