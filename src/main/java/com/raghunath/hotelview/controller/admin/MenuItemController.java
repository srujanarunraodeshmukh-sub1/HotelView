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

    @PostMapping("/add")
    public ApiResponse addMenuItem(@RequestBody MenuItemRequest request){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String hotelId = auth.getName();
        // Passing the hotelId (adminId) and the request containing the manual shortCode
        String message = menuItemService.addMenuItem(request, hotelId);
        return new ApiResponse(message);
    }

    @GetMapping("/allmenuitem")
    public Page<MenuItem> getAllMenuItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return menuItemService.getAllHotelItems(hotelId, page, size);
    }

    @GetMapping("/category")
    public List<MenuItem> getCategoryItems(@RequestParam String category){
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return menuItemService.getCategoryItems(hotelId, category);
    }

    /**
     * PRODUCTION GRADE: SHORTCODE + NAME SEARCH
     * Prioritizes items where the shortCode matches exactly first.
     */
    @GetMapping("/search")
    public List<MenuItem> searchMenuItems(@RequestParam String query) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        // Now calls the updated service logic for prioritized search
        return menuItemService.searchMenuItems(hotelId, query);
    }

    @GetMapping("/details")
    public MenuItem getMenuItemByName(@RequestParam String name) {
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();
        return menuItemService.getMenuItemByHotelAndName(hotelId, name);
    }
}