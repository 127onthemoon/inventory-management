package com.cumt.inventory.controller.web;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.dto.StockInRequest;
import com.cumt.inventory.dto.StockOperationResult;
import com.cumt.inventory.dto.StockOutRequest;
import com.cumt.inventory.entity.ChangeType;
import com.cumt.inventory.repository.StockFlowRepository;
import com.cumt.inventory.repository.StockInOrderRepository;
import com.cumt.inventory.repository.StockOutOrderRepository;
import com.cumt.inventory.service.ProductService;
import com.cumt.inventory.service.StatisticsService;
import com.cumt.inventory.service.StockService;
import com.cumt.inventory.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * 入库、出库、库存流水与一致性核对页面。
 * 表单页打开时生成 requestId（UUID）放入隐藏域：同一个表单无论被提交多少次，只会生效一次。
 */
@Controller
public class StockController {

    private final StockService stockService;
    private final ProductService productService;
    private final SupplierService supplierService;
    private final StatisticsService statisticsService;
    private final StockInOrderRepository stockInOrderRepository;
    private final StockOutOrderRepository stockOutOrderRepository;
    private final StockFlowRepository stockFlowRepository;

    public StockController(StockService stockService, ProductService productService,
                           SupplierService supplierService, StatisticsService statisticsService,
                           StockInOrderRepository stockInOrderRepository,
                           StockOutOrderRepository stockOutOrderRepository,
                           StockFlowRepository stockFlowRepository) {
        this.stockService = stockService;
        this.productService = productService;
        this.supplierService = supplierService;
        this.statisticsService = statisticsService;
        this.stockInOrderRepository = stockInOrderRepository;
        this.stockOutOrderRepository = stockOutOrderRepository;
        this.stockFlowRepository = stockFlowRepository;
    }

    // ------------------------------------------------------------------ 入库

    @GetMapping("/stock/in")
    public String inList(@RequestParam(required = false) Long productId,
                         @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", stockInOrderRepository.search(productId, PageUtils.of(page, 10)));
        model.addAttribute("productId", productId);
        model.addAttribute("products", productService.activeProducts());
        return "stock/in-list";
    }

    @GetMapping("/stock/in/new")
    public String inForm(@RequestParam(required = false) Long productId, Model model) {
        StockInRequest form = new StockInRequest();
        form.setProductId(productId);
        form.setRequestId(UUID.randomUUID().toString());
        model.addAttribute("form", form);
        fillInOptions(model);
        return "stock/in-form";
    }

    @PostMapping("/stock/in")
    public String createIn(@Valid @ModelAttribute("form") StockInRequest form, BindingResult binding,
                           Authentication auth, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            fillInOptions(model);
            return "stock/in-form";
        }
        try {
            StockOperationResult r = stockService.stockIn(form, auth.getName());
            ra.addFlashAttribute(r.duplicate() ? "warning" : "success", message(r));
            return "redirect:/stock/in";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            fillInOptions(model);
            return "stock/in-form";
        }
    }

    // ------------------------------------------------------------------ 出库

    @GetMapping("/stock/out")
    public String outList(@RequestParam(required = false) Long productId,
                          @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", stockOutOrderRepository.search(productId, PageUtils.of(page, 10)));
        model.addAttribute("productId", productId);
        model.addAttribute("products", productService.activeProducts());
        return "stock/out-list";
    }

    @GetMapping("/stock/out/new")
    public String outForm(@RequestParam(required = false) Long productId, Model model) {
        StockOutRequest form = new StockOutRequest();
        form.setProductId(productId);
        form.setRequestId(UUID.randomUUID().toString());
        model.addAttribute("form", form);
        model.addAttribute("products", productService.activeProducts());
        return "stock/out-form";
    }

    @PostMapping("/stock/out")
    public String createOut(@Valid @ModelAttribute("form") StockOutRequest form, BindingResult binding,
                            Authentication auth, Model model, RedirectAttributes ra) {
        if (binding.hasErrors()) {
            model.addAttribute("products", productService.activeProducts());
            return "stock/out-form";
        }
        try {
            StockOperationResult r = stockService.stockOut(form, auth.getName());
            ra.addFlashAttribute(r.duplicate() ? "warning" : "success", message(r));
            return "redirect:/stock/out";
        } catch (BusinessException e) {
            // 库存不足等业务拒绝：留在表单页提示，幂等号保持不变（本次未生效，可修改数量后再次提交）
            model.addAttribute("error", e.getMessage());
            model.addAttribute("products", productService.activeProducts());
            return "stock/out-form";
        }
    }

    // ------------------------------------------------------------------ 流水 & 一致性

    @GetMapping("/flows")
    public String flows(@RequestParam(required = false) Long productId,
                        @RequestParam(required = false) ChangeType type,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("page", stockFlowRepository.search(productId, type, PageUtils.of(page, 15)));
        model.addAttribute("productId", productId);
        model.addAttribute("type", type);
        model.addAttribute("products", productService.activeProducts());
        return "stock/flows";
    }

    @GetMapping("/consistency")
    public String consistency(Model model) {
        model.addAttribute("report", statisticsService.consistency());
        return "stock/consistency";
    }

    private void fillInOptions(Model model) {
        model.addAttribute("products", productService.activeProducts());
        model.addAttribute("suppliers", supplierService.findAll());
    }

    private static String message(StockOperationResult r) {
        String action = r.type() == ChangeType.IN ? "入库" : "出库";
        if (r.duplicate()) {
            return "检测到重复提交，未重复" + action + "（原单据 " + r.orderNo() + "）";
        }
        return action + "成功：单据 " + r.orderNo() + "，库存 " + r.beforeStock() + " → " + r.afterStock();
    }
}
