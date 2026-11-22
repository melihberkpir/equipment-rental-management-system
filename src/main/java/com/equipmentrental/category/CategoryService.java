package com.equipmentrental.category;

import com.equipmentrental.category.dto.CategoryRequest;
import com.equipmentrental.category.dto.CategoryResponse;
import com.equipmentrental.common.PageRequests;
import com.equipmentrental.common.PageResponse;
import com.equipmentrental.domain.Category;
import com.equipmentrental.exception.ConflictException;
import com.equipmentrental.exception.NotFoundException;
import com.equipmentrental.repository.CategoryRepository;
import com.equipmentrental.repository.EquipmentRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "name");

    private final CategoryRepository categories;
    private final EquipmentRepository equipment;

    public CategoryService(CategoryRepository categories, EquipmentRepository equipment) {
        this.categories = categories;
        this.equipment = equipment;
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(int page, int limit, String sort) {
        return PageResponse.from(categories.findAll(PageRequests.of(page, limit, sort, SORTABLE_FIELDS, "name")),
                CategoryResponse::from);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return CategoryResponse.from(find(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().strip();
        if (categories.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A category with this name already exists");
        }
        return CategoryResponse.from(categories.saveAndFlush(new Category(name, request.description())));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = find(id);
        String name = request.name().strip();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("A category with this name already exists");
        }
        category.setName(name);
        category.setDescription(request.description());
        return CategoryResponse.from(categories.saveAndFlush(category));
    }

    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (equipment.existsByCategoryId(id)) {
            throw new ConflictException("Category cannot be deleted while it contains equipment");
        }
        categories.delete(category);
        categories.flush();
    }

    private Category find(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category not found"));
    }
}
