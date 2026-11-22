package com.equipmentrental.common;

import com.equipmentrental.exception.BadRequestException;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds validated page requests from the API's page, limit, and "field,direction" parameters. */
public final class PageRequests {

    private static final int MAX_LIMIT = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int limit, String sort, Set<String> sortableFields, String defaultField) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new BadRequestException("limit must be between 1 and " + MAX_LIMIT);
        }
        if ((long) page * limit > Integer.MAX_VALUE) {
            throw new BadRequestException("page is too large");
        }
        return PageRequest.of(page, limit, parseSort(sort, sortableFields, defaultField));
    }

    private static Sort parseSort(String sort, Set<String> sortableFields, String defaultField) {
        String field = defaultField;
        Sort.Direction direction = Sort.Direction.ASC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", -1);
            if (parts.length > 2) {
                throw new BadRequestException("sort must use the format field,asc or field,desc");
            }
            field = parts[0].strip();
            if (!sortableFields.contains(field)) {
                throw new BadRequestException("sort field must be one of " + sortableFields.stream().sorted().toList());
            }
            if (parts.length == 2) {
                direction = parseDirection(parts[1]);
            }
        }
        Sort result = Sort.by(direction, field);
        return "id".equals(field) ? result : result.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    private static Sort.Direction parseDirection(String value) {
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new BadRequestException("sort direction must be asc or desc");
        };
    }
}
