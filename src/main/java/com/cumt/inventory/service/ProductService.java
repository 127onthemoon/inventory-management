package com.cumt.inventory.service;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.common.ResourceNotFoundException;
import com.cumt.inventory.dto.ProductForm;
import com.cumt.inventory.entity.Product;
import com.cumt.inventory.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 商品管理。注意：本类不提供任何修改库存的方法 —— 库存只能经 {@link StockService} 变更。
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StockFlowRepository stockFlowRepository;

    public ProductService(ProductRepository productRepository, CategoryRepository categoryRepository,
                          SupplierRepository supplierRepository, StockFlowRepository stockFlowRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.stockFlowRepository = stockFlowRepository;
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String keyword, Long categoryId, Pageable pageable) {
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return productRepository.search(kw, categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Product> activeProducts() {
        return productRepository.findByStatusOrderByCodeAsc(1);
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("商品不存在：id=" + id));
    }

    @Transactional
    public Product save(ProductForm form) {
        String code = form.getCode().trim();
        boolean dup = form.getId() == null ? productRepository.existsByCode(code)
                : productRepository.existsByCodeAndIdNot(code, form.getId());
        if (dup) {
            throw new BusinessException("DUPLICATE_CODE", "商品编码【" + code + "】已存在");
        }
        Product p = form.getId() == null ? new Product() : get(form.getId());
        p.setCode(code);
        p.setName(form.getName().trim());
        p.setCategory(categoryRepository.findById(form.getCategoryId())
                .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "所选分类不存在")));
        p.setSupplier(form.getSupplierId() == null ? null : supplierRepository.findById(form.getSupplierId())
                .orElseThrow(() -> new BusinessException("VALIDATION_ERROR", "所选供应商不存在")));
        p.setUnit(form.getUnit().trim());
        p.setPrice(form.getPrice());
        p.setWarningThreshold(form.getWarningThreshold());
        p.setStatus(form.getStatus());
        try {
            return productRepository.saveAndFlush(p);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("DUPLICATE_CODE", "商品编码【" + code + "】已存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        Product p = get(id);
        if (stockFlowRepository.countByProductId(id) > 0) {
            throw new BusinessException("IN_USE", "商品【" + p.getName() + "】已有库存流水，不能删除，可将其停用");
        }
        productRepository.delete(p);
    }

    public ProductForm toForm(Product p) {
        ProductForm f = new ProductForm();
        f.setId(p.getId());
        f.setCode(p.getCode());
        f.setName(p.getName());
        f.setCategoryId(p.getCategory().getId());
        f.setSupplierId(p.getSupplier() == null ? null : p.getSupplier().getId());
        f.setUnit(p.getUnit());
        f.setPrice(p.getPrice());
        f.setWarningThreshold(p.getWarningThreshold());
        f.setStatus(p.getStatus());
        return f;
    }
}
