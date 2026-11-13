package com.ecommerce.catalog.dto;

import java.util.List;

/**
 * One page of results, with what a list screen needs to paginate. A plain
 * record rather than Spring's {@code Page}, whose JSON shape is not a stable
 * contract.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(content, page, size, totalElements, totalPages);
    }
}
