package com.cumt.inventory.service;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.common.ResourceNotFoundException;
import com.cumt.inventory.dto.CategoryForm;
import com.cumt.inventory.entity.Category;
import com.cumt.inventory.repository.CategoryRepository;
import com.cumt.inventory.repository.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 分类管理 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Category get(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("分类不存在"));
    }

    @Transactional(readOnly = true)
    public long productCount(Long categoryId) {
        return productRepository.countByCategoryId(categoryId);
    }

    @Transactional
    public Category save(CategoryForm form) {
        String name = form.getName().trim();
        boolean dup = form.getId() == null ? categoryRepository.existsByName(name)
                : categoryRepository.existsByNameAndIdNot(name, form.getId());
        if (dup) {
            throw new BusinessException("DUPLICATE_NAME", "分类名称【" + name + "】已存在");
        }
        Category c = form.getId() == null ? new Category() : get(form.getId());
        c.setName(name);
        c.setDescription(form.getDescription());
        try {
            return categoryRepository.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            // 并发下两人同时新建同名分类：由唯一约束 uk_category_name 兜底
            throw new BusinessException("DUPLICATE_NAME", "分类名称【" + name + "】已存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        Category c = get(id);
        if (productRepository.countByCategoryId(id) > 0) {
            throw new BusinessException("IN_USE", "分类【" + c.getName() + "】下还有商品，不能删除");
        }
        categoryRepository.delete(c);
    }

    public CategoryForm toForm(Category c) {
        CategoryForm f = new CategoryForm();
        f.setId(c.getId());
        f.setName(c.getName());
        f.setDescription(c.getDescription());
        return f;
    }
}
