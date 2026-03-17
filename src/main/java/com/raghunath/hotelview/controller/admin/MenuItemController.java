package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.dto.admin.ApiResponse;
import com.raghunath.hotelview.dto.admin.MenuItemRequest;
import com.raghunath.hotelview.entity.MenuItem;
import com.raghunath.hotelview.service.admin.MenuItemService;
import lombok.RequiredArgsConstructor;
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
    @PostMapping("/add")
    public ApiResponse addMenuItem(@RequestBody MenuItemRequest request){

        String message = menuItemService.addMenuItem(request);

        return new ApiResponse(message);

    }

    // all items of hotel
    @GetMapping("/allmenuitem")
    public List<MenuItem> getAllMenuItems(){

        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();

        String adminId = auth.getName();

        return menuItemService.getAllHotelItems(adminId);

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
}