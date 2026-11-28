package com.ecommerce.catalog.service;

import com.ecommerce.catalog.dto.AdminVariantResponse;
import com.ecommerce.catalog.entity.Variant;
import com.ecommerce.catalog.entity.VariantImage;
import com.ecommerce.catalog.repository.ProductRepository;
import com.ecommerce.catalog.repository.VariantRepository;
import com.ecommerce.catalog.support.ImageFormat;
import com.ecommerce.catalog.support.ImageStorage;
import com.ecommerce.common.exception.BadRequestException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The photos of a variant (C06): uploaded one by one, deleted, reordered.
 * Position 1 is the main thumbnail; positions always run 1, 2, 3... with
 * no gap, so the storefront takes the first image as is.
 *
 * <p>Every write locks the product first, like the other writes on its
 * variants (C04): two uploads at once cannot both take the tenth place.
 */
@Service
@RequiredArgsConstructor
public class VariantImageService {

    private final VariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ImageStorage storage;

    @Value("${catalog.images.max-per-variant:10}")
    private int maxPerVariant;

    /** The limit of the multipart forms, checked here too: it then holds whatever the server in front. */
    @Value("${spring.servlet.multipart.max-file-size:5MB}")
    private DataSize maxFileSize;

    @Transactional
    public AdminVariantResponse upload(Long variantId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file is required and must not be empty");
        }
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new MaxUploadSizeExceededException(maxFileSize.toBytes());
        }
        byte[] content = read(file);
        ImageFormat format = ImageFormat.detect(content)
                .orElseThrow(() -> new BadRequestException("Only JPEG, PNG and WebP images are accepted"));

        Variant variant = lockedVariant(variantId);
        if (variant.getImages().size() >= maxPerVariant) {
            throw new ConflictException("Variant " + variant.getSku() + " already has " + maxPerVariant
                    + " images: delete one before adding another");
        }
        // Le fichier est retire si la transaction echoue apres coup (voir ImageStorage).
        String name = storage.store(content, format);
        variant.addImage(storage.urlOf(name));
        variantRepository.saveAndFlush(variant);
        return AdminVariantResponse.from(variant);
    }

    /** Removes the image, then its file once the deletion is committed; the next ones move up. */
    @Transactional
    public AdminVariantResponse delete(Long variantId, Long imageId) {
        Variant variant = lockedVariant(variantId);
        VariantImage image = variant.getImages().stream()
                .filter(candidate -> candidate.getId().equals(imageId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image " + imageId + " not found on variant " + variantId));
        variant.getImages().remove(image);
        renumber(variant.getImages());
        variantRepository.saveAndFlush(variant);
        storage.deleteAfterCommit(List.of(image.getUrl()));
        return AdminVariantResponse.from(variant);
    }

    /** @param imageIds every image of the variant, once each, in the new order */
    @Transactional
    public AdminVariantResponse reorder(Long variantId, List<Long> imageIds) {
        Variant variant = lockedVariant(variantId);
        Set<Long> current = new HashSet<>();
        variant.getImages().forEach(image -> current.add(image.getId()));
        if (imageIds.size() != current.size() || !current.equals(new HashSet<>(imageIds))) {
            throw new BadRequestException("imageIds must list each image of variant " + variantId
                    + " exactly once: " + current.stream().sorted().toList());
        }
        for (VariantImage image : variant.getImages()) {
            image.setPosition(imageIds.indexOf(image.getId()) + 1);
        }
        variant.getImages().sort(Comparator.comparingInt(VariantImage::getPosition));
        variantRepository.saveAndFlush(variant);
        return AdminVariantResponse.from(variant);
    }

    private static void renumber(List<VariantImage> images) {
        images.sort(Comparator.comparingInt(VariantImage::getPosition).thenComparing(VariantImage::getId));
        for (int i = 0; i < images.size(); i++) {
            images.get(i).setPosition(i + 1);
        }
    }

    private Variant lockedVariant(Long variantId) {
        Variant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant " + variantId + " not found"));
        productRepository.findForUpdateById(variant.getProduct().getId());
        return variant;
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read the uploaded file", ex);
        }
    }
}
