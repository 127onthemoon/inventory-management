package com.cumt.inventory.controller.web;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.dto.ProductForm;
import com.cumt.inventory.entity.Product;
import com.cumt.inventory.repository.StockFlowRepository;
import com.cumt.inventory.service.CategoryService;
import com.cumt.inventory.service.ProductService;
import com.cumt.inventory.service.StatisticsService;
import com.cumt.inventory.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 商品管理 */
@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final SupplierService supplierService;
    private final StatisticsService statisticsService;
    private final StockFlowRepository stockFlowRepository;

    public ProductController(ProductService productService, CategoryService categoryService,
                             SupplierService supplierService, StatisticsService statisticsService,
                             StockFlowRepository stockFlowRepository) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.supplierService = supplierService;
        this.statisticsService = statisticsService;
        this.stockFlowRepository = stockFlowRepository;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", productService.search(keyword, categoryId, PageUtils.of(page, 10)));
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("categories", categoryService.findAll());
        return "products/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Product p = productService.get(id);
        model.addAttribute("product", p);
        model.addAttribute("check", statisticsService.check(p));
        model.addAttribute("flows", stockFlowRepository.search(id, null,
                PageRequest.of(0, 15, Sort.by(Sort.Direction.DESC, "id"))).getContent());
        return "products/detail";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('ADMIN')")
    public String createForm(Model model) {
        model.addAttribute("form", new ProductForm());
        fillOptions(model);
        return "products/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('ADMIN')")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", productService.toForm(productService.get(id)));
        fillOptions(model);
        return "products/form";
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public String save(@Valid @ModelAttribute("form") ProductForm form, BindingResult binding,
                       RedirectAttributes ra, Model model) {
        if (binding.hasErrors()) {
            fillOptions(model);
            return "products/form";
        }
        try {
            productService.save(form);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            fillOptions(model);
            return "products/form";
        }
        ra.addFlashAttribute("success", "商品已保存");
        return "redirect:/products";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            productService.delete(id);
            ra.addFlashAttribute("success", "商品已删除");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/products";
    }

    private void fillOptions(Model model) {
        model.addAttribute("categories", categoryService.findAll());
        model.addAttribute("suppliers", supplierService.findAll());
    }
}
