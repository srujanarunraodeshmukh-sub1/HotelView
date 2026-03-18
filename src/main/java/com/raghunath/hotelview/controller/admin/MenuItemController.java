package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.dto.admin.ApiResponse;
import com.raghunath.hotelview.dto.admin.MenuItemRequest;
import com.raghunath.hotelview.entity.MenuItem;
import com.raghunath.hotelview.service.admin.MenuItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    // Add product
    // Add product
    @PostMapping("/add")
    public ApiResponse addMenuItem(@RequestBody MenuItemRequest request){

        // 1. Get the adminId from the token (the security context)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String adminId = auth.getName();

        // 2. Pass both the request AND the adminId to your service
        String message = menuItemService.addMenuItem(request, adminId);

        return new ApiResponse(message);
    }

    // all items of hotel
    @GetMapping("/allmenuitem")
    public Page<MenuItem> getAllMenuItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) { // This limits it to 10 at a time!

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String hotelId = auth.getName();

        return menuItemService.getAllHotelItems(hotelId, page, size);
    }

    // items by category
    @GetMapping("/category")
    public List<MenuItem> getCategoryItems(@RequestParam String category){

        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        String adminId = auth.getName();

        return menuItemService.getCategoryItems(adminId,category);

    }

    // specific item
    @GetMapping("/menuitem")
    public MenuItem getMenuItem(@RequestParam String itemId){

        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        String adminId = auth.getName();

        return menuItemService.getMenuItem(adminId,itemId);

    }

    @GetMapping("/search")
    public MenuItem getMenuItemByName(@RequestParam String name) {
        // 1. Get the Hotel ID directly from the optimized Token
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String hotelId = auth.getName();

        // 2. Search using BOTH the Hotel ID and the Menu Name
        return menuItemService.getMenuItemByHotelAndName(hotelId, name);
    }
}