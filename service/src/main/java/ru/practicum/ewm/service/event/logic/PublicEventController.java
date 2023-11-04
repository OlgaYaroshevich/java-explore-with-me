package ru.practicum.ewm.service.event.logic;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.practicum.ewm.service.event.models.event.EventFullDto;
import ru.practicum.ewm.service.event.models.event.EventShortDto;
import ru.practicum.ewm.service.util.UtilConstants;
import ru.practicum.ewm.service.util.exception.BadRequestException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping(path = "/events")
@RequiredArgsConstructor
public class PublicEventController {
    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private final EventService eventService;

    @GetMapping()
    public List<EventShortDto> getAll(@RequestParam(defaultValue = "") String text,
                                      @RequestParam(required = false) List<Long> categories,
                                      @RequestParam(required = false) Boolean paid,
                                      @RequestParam(required = false) @DateTimeFormat(pattern = UtilConstants.DATETIME_FORMAT) LocalDateTime rangeStart,
                                      @RequestParam(required = false) @DateTimeFormat(pattern = UtilConstants.DATETIME_FORMAT) LocalDateTime rangeEnd,
                                      @RequestParam(defaultValue = "false") boolean onlyAvailable,
                                      @RequestParam(defaultValue = "VIEWS") SortMode sort,
                                      @Valid @RequestParam(defaultValue = "0") @PositiveOrZero int from,
                                      @Valid @RequestParam(defaultValue = "10") @Positive int size, HttpServletRequest request) {
        if (rangeStart != null && rangeEnd != null && rangeStart.isAfter(rangeEnd)) {
            throw new BadRequestException("Дата начала должна предшествовать дате окончания.");
        }
        return eventService.getAllPublic(text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size, request);
    }

    @GetMapping("{eventId}")
    public EventFullDto getById(@PathVariable long eventId, HttpServletRequest request) {
        return eventService.getByIdPublic(eventId, request);
    }

    public enum SortMode {
        EVENT_DATE,
        VIEWS
    }
}
