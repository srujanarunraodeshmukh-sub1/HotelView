package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.entity.MenuItem;
import com.raghunath.hotelview.service.admin.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.web.PagedModel; // 👈 Safely handles 100k user page serialization wrappers
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    private String getHotelId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @GetMapping("/version")
    public ResponseEntity<MenuVersionResponse> getMenuVersion() {
        return ResponseEntity.ok(menuItemService.getMenuMetadata(getHotelId()));
    }

    @GetMapping("/updates")
    public ResponseEntity<List<MenuItem>> getMenuUpdates(@RequestParam long lastSync) {
        return ResponseEntity.ok(menuItemService.getChangedItems(getHotelId(), lastSync));
    }

    // ====================================================================
    // 1 LAC USER HIGH TRAFFIC SNAPSHOT API (Cached at service layer)
    // ====================================================================
    @GetMapping("/all-cached")
    public ResponseEntity<List<MenuItemSummaryDTO>> getAllForCache() {
        return ResponseEntity.ok(menuItemService.getAllItemsForCache(getHotelId()));
    }

    // searching implemented on frontend side
    @GetMapping("/search")
    public List<MenuItemSummaryDTO> searchMenuItems(@RequestParam(name = "query") String query) {
        return menuItemService.searchMenuItems(getHotelId(), query);
    }

    @PatchMapping("/{itemId}/availability")
    public ResponseEntity<MenuItemSummaryDTO> toggleAvailability(
            @PathVariable(name = "itemId") String itemId,
            @RequestParam(name = "available") boolean available) {
        return ResponseEntity.ok(menuItemService.toggleAvailabilityAndReturnDto(getHotelId(), itemId, available));
    }

    @GetMapping("/details")
    public ResponseEntity<MenuItemSummaryDTO> getMenuItemByName(@RequestParam String name) {
        MenuItem item = menuItemService.getMenuItemByHotelAndName(getHotelId(), name);
        return ResponseEntity.ok(MenuItemSummaryDTO.builder()
                .id(item.getId())
                .name(item.getName())
                .price(item.getPrice())
                .category(item.getCategory())
                .shortCode(item.getShortCode())
                .isAvailable(item.getIsAvailable())
                .isVeg(item.getIsVeg())
                .imageUrl(item.getImageUrl())
                .description(item.getDescription())
                .build());
    }

    @PostMapping("/add")
    public ApiResponse addMenuItem(@RequestBody MenuItemRequest request){
        String message = menuItemService.addMenuItem(request, getHotelId());
        return new ApiResponse(message);
    }

    // Not in production
    @GetMapping("/allmenuitem")
    public ResponseEntity<PagedModel<MenuItem>> getAllMenuItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<MenuItem> items = menuItemService.getAllHotelItems(getHotelId(), page, size);
        return ResponseEntity.ok(new PagedModel<>(items)); // 👈 Bypasses PageImpl serialization logging warnings
    }

    // Not in production
    @GetMapping("/category")
    public List<MenuItem> getCategoryItems(@RequestParam String category){
        return menuItemService.getCategoryItems(getHotelId(), category);
    }

    @PutMapping("/{itemId}")
    public ResponseEntity<MenuItemSummaryDTO> updateMenuItem(
            @PathVariable String itemId,
            @Valid @RequestBody MenuItemUpdateDTO updateRequest) {
        return ResponseEntity.ok(menuItemService.updateMenuItem(getHotelId(), itemId, updateRequest));
    }
}