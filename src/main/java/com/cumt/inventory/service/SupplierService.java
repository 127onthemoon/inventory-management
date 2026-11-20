package com.cumt.inventory.service;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.common.ResourceNotFoundException;
import com.cumt.inventory.dto.SupplierForm;
import com.cumt.inventory.entity.Supplier;
import com.cumt.inventory.repository.ProductRepository;
import com.cumt.inventory.repository.StockInOrderRepository;
import com.cumt.inventory.repository.SupplierRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 供应商管理 */
@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final StockInOrderRepository stockInOrderRepository;

    public SupplierService(SupplierRepository supplierRepository, ProductRepository productRepository,
                           StockInOrderRepository stockInOrderRepository) {
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.stockInOrderRepository = stockInOrderRepository;
    }

    @Transactional(readOnly = true)
    public List<Supplier> findAll() {
        return supplierRepository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public Page<Supplier> search(String keyword, Pageable pageable) {
        return StringUtils.hasText(keyword) ? supplierRepository.findByNameContaining(keyword.trim(), pageable)
                : supplierRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Supplier get(Long id) {
        return supplierRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("供应商不存在"));
    }

    @Transactional
    public Supplier save(SupplierForm form) {
        String name = form.getName().trim();
        boolean dup = form.getId() == null ? supplierRepository.existsByName(name)
                : supplierRepository.existsByNameAndIdNot(name, form.getId());
        if (dup) {
            throw new BusinessException("DUPLICATE_NAME", "供应商【" + name + "】已存在");
        }
        Supplier s = form.getId() == null ? new Supplier() : get(form.getId());
        s.setName(name);
        s.setContactPerson(form.getContactPerson());
        s.setPhone(form.getPhone());
        s.setEmail(StringUtils.hasText(form.getEmail()) ? form.getEmail().trim() : null);
        s.setAddress(form.getAddress());
        s.setRemark(form.getRemark());
        try {
            return supplierRepository.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException("DUPLICATE_NAME", "供应商【" + name + "】已存在");
        }
    }

    @Transactional
    public void delete(Long id) {
        Supplier s = get(id);
        if (productRepository.countBySupplierId(id) > 0 || stockInOrderRepository.countBySupplierId(id) > 0) {
            throw new BusinessException("IN_USE", "供应商【" + s.getName() + "】已被商品或入库单引用，不能删除");
        }
        supplierRepository.delete(s);
    }

    public SupplierForm toForm(Supplier s) {
        SupplierForm f = new SupplierForm();
        f.setId(s.getId());
        f.setName(s.getName());
        f.setContactPerson(s.getContactPerson());
        f.setPhone(s.getPhone());
        f.setEmail(s.getEmail());
        f.setAddress(s.getAddress());
        f.setRemark(s.getRemark());
        return f;
    }
}
