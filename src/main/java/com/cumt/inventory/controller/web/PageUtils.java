package com.cumt.inventory.controller.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** 分页工具：页码从 0 开始，默认每页 10 条，按 id 倒序 */
final class PageUtils {
    private PageUtils() {
    }

    static Pageable of(int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "id"));
    }
}
