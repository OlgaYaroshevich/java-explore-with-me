package ru.practicum.ewm.service.user.logic;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.service.user.models.UserDto;
import ru.practicum.ewm.service.util.exception.BadRequestException;

import javax.validation.Valid;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;

@RestController
@RequestMapping(path = "/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;

    @GetMapping()
    public List<UserDto> get(@RequestParam(required = false) List<Long> ids,
                             @Valid @RequestParam(defaultValue = "0") @PositiveOrZero int from,
                             @Valid @RequestParam(defaultValue = "10") @Positive int size) {
        return userService.get(ids, from, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody UserDto userDto) {
        String[] parts = userDto.getEmail().split("@");
        String login = parts[0];
        String domain = parts[1];
        String[] domainParts = domain.split("\\.");
        if (login.length() > 64) {
            throw new BadRequestException("Логин слишком длинный");
        }
        for (String domainPart : domainParts) {
            if (domainPart.length() > 64) {
                throw new BadRequestException("Домен слишком длинный");
            }
        }
        return userService.create(userDto);
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long userId) {
        userService.delete(userId);
    }
}
