package com.cumt.inventory.controller.web;

import com.cumt.inventory.service.StatisticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 首页仪表盘 */
@Controller
public class DashboardController {

    private final StatisticsService statisticsService;

    public DashboardController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("stats", statisticsService.dashboard());
        return "dashboard";
    }
}
