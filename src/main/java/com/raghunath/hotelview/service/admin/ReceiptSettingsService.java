package com.raghunath.hotelview.service.admin;

import com.raghunath.hotelview.dto.admin.ReceiptSettingsDTO;
import com.raghunath.hotelview.entity.ReceiptSettings;
import com.raghunath.hotelview.repository.ReceiptSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptSettingsService {

    private final ReceiptSettingsRepository repository;

    /**
     * Fetches current configuration or returns hardcoded business fallbacks if missing
     */
    public ReceiptSettingsDTO getSettings(String hotelId, String receiptType) {
        return repository.findByHotelIdAndReceiptType(hotelId, receiptType)
                .map(this::mapToDTO)
                .orElseGet(() -> getFallbackDefaults(receiptType));
    }

    /**
     * Saves or updates configuration properties via dynamic Upsert logic execution
     */
    @Transactional
    public String saveSettings(String hotelId, String receiptType, ReceiptSettingsDTO dto) {
        ReceiptSettings settings = repository.findByHotelIdAndReceiptType(hotelId, receiptType)
                .orElse(ReceiptSettings.builder()
                        .hotelId(hotelId)
                        .receiptType(receiptType)
                        .build());

        // Map switches cleanly
        settings.setPrintRestaurantName(dto.isPrintRestaurantName());
        settings.setPrintBusinessAddress(dto.isPrintBusinessAddress());
        settings.setPrintCustomerNumber(dto.isPrintCustomerNumber());
        settings.setIncludeRestaurantLogo(dto.isIncludeRestaurantLogo());
        settings.setPrintDateTime(dto.isPrintDateTime());
        settings.setShowOrderTypeLabel(dto.isShowOrderTypeLabel());
        settings.setDisplayOrderId(dto.isDisplayOrderId());
        settings.setShowCustomerDetails(dto.isShowCustomerDetails());
        settings.setPrintUpiAndQr(dto.isPrintUpiAndQr());
        settings.setPrintGreetingNote(dto.isPrintGreetingNote());
        settings.setPrintItemQuantities(dto.isPrintItemQuantities());

        repository.save(settings);
        log.info("Successfully updated '{}' receipt settings for hotel: {}", receiptType, hotelId);
        return "Receipt settings for " + receiptType + " saved successfully.";
    }

    private ReceiptSettingsDTO mapToDTO(ReceiptSettings entity) {
        return ReceiptSettingsDTO.builder()
                .printRestaurantName(entity.isPrintRestaurantName())
                .printBusinessAddress(entity.isPrintBusinessAddress())
                .printCustomerNumber(entity.isPrintCustomerNumber())
                .includeRestaurantLogo(entity.isIncludeRestaurantLogo())
                .printDateTime(entity.isPrintDateTime())
                .showOrderTypeLabel(entity.isShowOrderTypeLabel())
                .displayOrderId(entity.isDisplayOrderId())
                .showCustomerDetails(entity.isShowCustomerDetails())
                .printUpiAndQr(entity.isPrintUpiAndQr())
                .printGreetingNote(entity.isPrintGreetingNote())
                .printItemQuantities(entity.isPrintItemQuantities())
                .build();
    }

    private ReceiptSettingsDTO getFallbackDefaults(String receiptType) {
        if ("TABLE_HOME".equalsIgnoreCase(receiptType)) {
            return ReceiptSettingsDTO.builder()
                    .printRestaurantName(true)
                    .printBusinessAddress(true)
                    .printCustomerNumber(true)
                    .includeRestaurantLogo(true)
                    .printDateTime(true)
                    .showOrderTypeLabel(true)
                    .displayOrderId(true)
                    .showCustomerDetails(true)
                    .printUpiAndQr(true)
                    .printGreetingNote(true)
                    .printItemQuantities(true)
                    .build();
        } else { // Fallback targets "INSTANT" configuration defaults directly
            return ReceiptSettingsDTO.builder()
                    .printRestaurantName(true)
                    .printBusinessAddress(false) // Default false
                    .printCustomerNumber(false)  // Default false
                    .includeRestaurantLogo(false) // Default false
                    .printDateTime(true)
                    .showOrderTypeLabel(false)   // Default false
                    .displayOrderId(true)
                    .showCustomerDetails(true)
                    .printUpiAndQr(true)
                    .printGreetingNote(true)
                    .printItemQuantities(true)
                    .build();
        }
    }
}