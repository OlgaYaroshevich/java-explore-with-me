package ru.practicum.ewm.service.category.logic;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.service.category.models.Category;
import ru.practicum.ewm.service.category.models.CategoryDto;
import ru.practicum.ewm.service.category.models.CategoryMapper;
import ru.practicum.ewm.service.category.models.CategoryRepository;
import ru.practicum.ewm.service.util.exception.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryDto create(CategoryDto categoryDto) {
        return CategoryMapper.INSTANCE.toDto(categoryRepository.save(CategoryMapper.INSTANCE.fromDto(categoryDto)));
    }

    @Transactional
    public CategoryDto update(long catId, CategoryDto categoryDto) {
        Category stored = findById(catId);
        Optional.ofNullable(categoryDto.getName()).ifPresent(stored::setName);
        return CategoryMapper.INSTANCE.toDto(categoryRepository.save(stored));
    }

    @Transactional
    public void delete(long catId) {
        findById(catId);
        categoryRepository.deleteById(catId);
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getAll(int from, int size) {
        return categoryRepository.findAll(PageRequest.of(from, size)).getContent().stream()
                .map(CategoryMapper.INSTANCE::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryDto getById(long catId) {
        return CategoryMapper.INSTANCE.toDto(findById(catId));
    }

    private Category findById(long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + id + " не найдена"));
    }
}
