package com.etoile.server.controller;

import com.etoile.server.service.OrderService;
import com.etoile.shared.MenuItemDTO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
@CrossOrigin(origins = "*")
public class MenuController {

    private final OrderService orderService;

    public MenuController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<MenuItemDTO> getMenu(@RequestParam(required = false) String category) {
        return orderService.getMenu(category);
    }
}