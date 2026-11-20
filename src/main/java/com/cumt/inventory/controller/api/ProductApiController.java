package com.cumt.inventory.controller.api;

import com.cumt.inventory.common.ApiResponse;
import com.cumt.inventory.entity.Product;
import com.cumt.inventory.repository.ProductRepository;
import com.cumt.inventory.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 商品查询接口（只读） */
@RestController
@RequestMapping("/api/products")
public class ProductApiController {

    private final ProductService productService;
    private final ProductRepository productRepository;

    public ProductApiController(ProductService productService, ProductRepository productRepository) {
        this.productService = productService;
        this.productRepository = productRepository;
    }

    @GetMapping
    public ApiResponse<List<ProductView>> list() {
        return ApiResponse.ok(productRepository.findAll().stream()
                .map(p -> ProductView.of(p, p.getStock())).toList());
    }

    /** 查询单个商品的实时库存（直接查数据库最新值） */
    @GetMapping("/{id}")
    public ApiResponse<ProductView> get(@PathVariable Long id) {
        Product p = productService.get(id);
        return ApiResponse.ok(ProductView.of(p, productRepository.currentStock(id)));
    }
}
