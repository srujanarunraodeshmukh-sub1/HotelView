package com.raghunath.hotelview.controller.admin;

import com.raghunath.hotelview.dto.admin.*;
import com.raghunath.hotelview.entity.Admin;
import com.raghunath.hotelview.entity.MenuItem;
import com.raghunath.hotelview.service.admin.MenuItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @GetMapping("/all-cached")
    public List<MenuItemSummaryDTO> getAllForCache() {
        return menuItemService.getAllItemsForCache(getHotelId());
    }

    @GetMapping("/search")
    public List<MenuItemSummaryDTO> searchMenuItems(@RequestParam(name = "query") String query) {
        return menuItemService.searchMenuItems(getHotelId(), query);
    }

    @PatchMapping("/{itemId}/availability")
    public ResponseEntity<MenuItemSummaryDTO> toggleAvailability(
            @PathVariable(name = "itemId") String itemId, // Add (name = "itemId")
            @RequestParam(name = "available") boolean available) { // Add (name = "available")
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
    @CacheEvict(value = "menuCache", allEntries = true)
    public ApiResponse addMenuItem(@RequestBody MenuItemRequest request){
        String message = menuItemService.addMenuItem(request, getHotelId());
        return new ApiResponse(message);
    }

    @GetMapping("/allmenuitem")
    @Cacheable(value = "menuCache", key = "#root.target.getHotelId() + '-' + #page + '-' + #size")
    public Page<MenuItem> getAllMenuItems(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return menuItemService.getAllHotelItems(getHotelId(), page, size);
    }

    @GetMapping("/category")
    public List<MenuItem> getCategoryItems(@RequestParam String category){
        return menuItemService.getCategoryItems(getHotelId(), category);
    }

    @PutMapping("/{itemId}")
    @CacheEvict(value = "menuCache", allEntries = true)
    public ResponseEntity<MenuItemSummaryDTO> updateMenuItem(
            @PathVariable String itemId,
            @Valid @RequestBody MenuItemUpdateDTO updateRequest) {

        // In your Filter, you set 'hotelId' as the Principal. Fetch it like this:
        String hotelId = SecurityContextHolder.getContext().getAuthentication().getName();

        MenuItemSummaryDTO updatedItem = menuItemService.updateMenuItem(hotelId, itemId, updateRequest);
        return ResponseEntity.ok(updatedItem);
    }
}
