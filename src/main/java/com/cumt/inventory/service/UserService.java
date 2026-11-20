package com.cumt.inventory.service;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.common.ResourceNotFoundException;
import com.cumt.inventory.dto.UserForm;
import com.cumt.inventory.entity.User;
import com.cumt.inventory.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 用户管理（仅管理员可调用，权限在控制器层 @PreAuthorize 控制） */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("用户不存在"));
    }

    @Transactional
    public User save(UserForm form, String currentUsername) {
        User u;
        if (form.getId() == null) {
            if (userRepository.existsByUsername(form.getUsername())) {
                throw new BusinessException("DUPLICATE_NAME", "用户名【" + form.getUsername() + "】已存在");
            }
            if (!StringUtils.hasText(form.getPassword()) || form.getPassword().length() < 6) {
                throw new BusinessException("VALIDATION_ERROR", "新用户密码至少 6 位");
            }
            u = new User();
            u.setUsername(form.getUsername());
        } else {
            u = get(form.getId());
            if (u.getUsername().equals(currentUsername) && (!form.isEnabled() || form.getRole() != u.getRole())) {
                throw new BusinessException("FORBIDDEN_SELF", "不能禁用自己或修改自己的角色");
            }
        }
        if (StringUtils.hasText(form.getPassword())) {
            if (form.getPassword().length() < 6) {
                throw new BusinessException("VALIDATION_ERROR", "密码至少 6 位");
            }
            u.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        u.setRealName(form.getRealName().trim());
        u.setRole(form.getRole());
        u.setEnabled(form.isEnabled());
        return userRepository.save(u);
    }

    @Transactional
    public void toggleEnabled(Long id, String currentUsername) {
        User u = get(id);
        if (u.getUsername().equals(currentUsername)) {
            throw new BusinessException("FORBIDDEN_SELF", "不能禁用当前登录的账号");
        }
        u.setEnabled(!u.isEnabled());
    }

    public UserForm toForm(User u) {
        UserForm f = new UserForm();
        f.setId(u.getId());
        f.setUsername(u.getUsername());
        f.setRealName(u.getRealName());
        f.setRole(u.getRole());
        f.setEnabled(u.isEnabled());
        return f;
    }
}
