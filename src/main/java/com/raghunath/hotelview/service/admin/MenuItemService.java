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

    public String addMenuItem(MenuItemRequest request){

        MenuItem item = MenuItem.builder()
                .hotelId(request.getHotelId())
                .category(request.getCategory())
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .isVeg(request.getIsVeg())
                .isAvailable(request.getIsAvailable())
                .imageUrl(request.getImageUrl())
                .preparationTime(request.getPreparationTime())
                .createdAt(LocalDateTime.now())
                .isApproved(false)
                .build();

        menuItemRepository.save(item);

        return "Item added successfully";
    }

    public List<MenuItem> getAllHotelItems(String adminId){

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        return menuItemRepository
                .findByHotelIdAndIsApprovedTrue(admin.getHotelId());
    }

    public List<MenuItem> getCategoryItems(String adminId,String category){

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        return menuItemRepository
                .findByHotelIdAndCategoryAndIsApprovedTrue(admin.getHotelId(),category);
    }

    public MenuItem getMenuItem(String adminId,String itemId){

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        return menuItemRepository
                .findByHotelIdAndId(admin.getHotelId(),itemId)
                .orElseThrow(() -> new RuntimeException("Menu item not found"));
    }
}