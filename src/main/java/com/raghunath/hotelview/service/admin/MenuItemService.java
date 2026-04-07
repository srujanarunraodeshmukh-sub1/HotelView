package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.MenuItemRequest;
import com.raghunath.hotelview.entity.MenuItem;
import com.raghunath.hotelview.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;

    /**
     * 1. ADD ITEM
     * Saves the manual ShortCode entered by the Hotel Admin.
     */
    public String addMenuItem(MenuItemRequest request, String hotelIdFromToken) {
        MenuItem item = MenuItem.builder()
                .hotelId(hotelIdFromToken)
                .category(request.getCategory())
                .name(request.getName())
                // Storing the manual short code entered by the admin
                .shortCode(request.getShortCode())
                .description(request.getDescription())
                .price(request.getPrice())
                .isVeg(request.getIsVeg())
                .isAvailable(request.getIsAvailable())
                .imageUrl(request.getImageUrl())
                .preparationTime(request.getPreparationTime())
                .createdAt(LocalDateTime.now())
                .isApproved(true)
                .build();

        menuItemRepository.save(item);
        return "Item added successfully with code: " + request.getShortCode();
    }

    /**
     * 2. SEARCH MENU ITEMS
     * Prioritizes ShortCode matches, then Name matches.
     */
    public List<MenuItem> searchMenuItems(String hotelId, String query) {
        // Guard Clause: Only search if 1 or more characters are entered
        if (!StringUtils.hasText(query)) {
            return Collections.emptyList();
        }

        // Uses the optimized Repository @Query we created
        return menuItemRepository.findByHotelIdAndSearchQuery(hotelId, query.trim());
    }

    // 3. GET CATEGORY ITEMS
    public List<MenuItem> getCategoryItems(String hotelIdFromToken, String category) {
        return menuItemRepository.findByHotelIdAndCategoryAndIsApprovedTrue(hotelIdFromToken, category);
    }

    // 4. GET ALL ITEMS (PAGINATED)
    public Page<MenuItem> getAllHotelItems(String hotelId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return menuItemRepository.findByHotelIdAndIsApprovedTrue(hotelId, pageable);
    }

    // 5. GET ITEM DETAILS BY NAME
    public MenuItem getMenuItemByHotelAndName(String hotelId, String name) {
        return menuItemRepository.findByHotelIdAndName(hotelId, name)
                .orElseThrow(() -> new RuntimeException("Menu item '" + name + "' not found for this hotel."));
    }
}