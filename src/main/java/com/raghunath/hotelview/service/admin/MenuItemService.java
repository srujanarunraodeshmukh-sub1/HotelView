package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.MenuItemRequest;
import com.raghunath.hotelview.entity.Admin;
import com.raghunath.hotelview.entity.MenuItem;
import com.raghunath.hotelview.repository.AdminRepository;
import com.raghunath.hotelview.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final AdminRepository adminRepository;

    // 1. ADD ITEM
    public String addMenuItem(MenuItemRequest request, String hotelIdFromToken) {
        MenuItem item = MenuItem.builder()
                .hotelId(hotelIdFromToken) // Directly from token!
                .category(request.getCategory())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .isVeg(request.getIsVeg())
                .isAvailable(request.getIsAvailable())
                .imageUrl(request.getImageUrl())
                .preparationTime(request.getPreparationTime())
                .createdAt(LocalDateTime.now())
                .isApproved(true) // Set to true for your current testing
                .build();

        menuItemRepository.save(item);
        return "Item added successfully";
    }

    // 2. GET CATEGORY ITEMS
    public List<MenuItem> getCategoryItems(String hotelIdFromToken, String category) {
        // Zero extra DB calls. Straight to the menu items.
        return menuItemRepository.findByHotelIdAndCategoryAndIsApprovedTrue(hotelIdFromToken, category);
    }

    // Simplified: No Admin lookup needed!
    public List<MenuItem> getAllHotelItems(String hotelIdFromToken) {
        return menuItemRepository.findByHotelIdAndIsApprovedTrue(hotelIdFromToken);
    }

    // Simplified: Search by Hotel + Item ID
    public MenuItem getMenuItem(String hotelIdFromToken, String itemId) {
        return menuItemRepository.findByHotelIdAndId(hotelIdFromToken, itemId)
                .orElseThrow(() -> new RuntimeException("Menu item not found"));
    }

    public MenuItem getMenuItemByHotelAndName(String hotelId, String name) {
        return menuItemRepository.findByHotelIdAndName(hotelId, name)
                .orElseThrow(() -> new RuntimeException("Menu item '" + name + "' not found for this hotel."));
    }
}