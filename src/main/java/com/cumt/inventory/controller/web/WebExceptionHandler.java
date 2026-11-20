package com.cumt.inventory.controller.web;

import com.cumt.inventory.common.BusinessException;
import com.cumt.inventory.common.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 页面控制器的统一异常处理（只处理业务异常；权限异常交给 Spring Security，其余交给 /error 页）。
 */
@ControllerAdvice(basePackages = "com.cumt.inventory.controller.web")
public class WebExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(ResourceNotFoundException e, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("message", e.getMessage());
        return "error/error";
    }

    @ExceptionHandler({BusinessException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String business(Exception e, Model model) {
        model.addAttribute("status", 400);
        model.addAttribute("message", e instanceof BusinessException ? e.getMessage() : "请求参数不正确");
        return "error/error";
    }
}
