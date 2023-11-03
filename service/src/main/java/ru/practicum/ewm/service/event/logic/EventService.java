package ru.practicum.ewm.service.event.logic;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import ru.practicum.ewm.service.category.models.Category;
import ru.practicum.ewm.service.category.models.CategoryRepository;
import ru.practicum.ewm.service.event.models.event.*;
import ru.practicum.ewm.service.event.models.location.Location;
import ru.practicum.ewm.service.event.models.location.LocationDto;
import ru.practicum.ewm.service.event.models.location.LocationMapper;
import ru.practicum.ewm.service.event.models.location.LocationRepository;
import ru.practicum.ewm.service.participationRequest.models.*;
import ru.practicum.ewm.service.user.models.User;
import ru.practicum.ewm.service.user.models.UserRepository;
import ru.practicum.ewm.service.util.UtilConstants;
import ru.practicum.ewm.service.util.exception.ConflictException;
import ru.practicum.ewm.service.util.exception.NotFoundException;
import ru.practicum.ewm.stats.client.StatsClient;
import ru.practicum.ewm.stats.dto.EndpointHitDto;
import ru.practicum.ewm.stats.dto.ViewStatsDto;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final ParticipationRequestRepository participationRequestRepository;
    @Value("${STAT_SERVER_URL:http://localhost:9090}")
    private String statClientUrl;
    private StatsClient statsClient;

    @PostConstruct
    private void init() {
        statsClient = new StatsClient(statClientUrl);
    }

    @Transactional
    public EventFullDto create(long userId, EventNewDto eventNewDto) {
        if (LocalDateTime.now().plusHours(2).isAfter(eventNewDto.getEventTimestamp())) {
            throw new ConflictException("Дата события должна быть на 2 часа позже текущего времени или позднее.");
        }
        User user = findUserById(userId);
        Category category = findCategoryById(eventNewDto.getCategory());
        Location location = handleLocationDto(eventNewDto.getLocation());
        Event event = EventMapper.INSTANCE.fromDto(eventNewDto, category, location);
        event.setInitiator(user);
        event.setCreatedOn(LocalDateTime.now());
        event.setState(EventState.PENDING);
        if (eventNewDto.getPaid() == null) {
            event.setPaid(false);
        }
        if (eventNewDto.getParticipantLimit() == null) {
            event.setParticipantLimit(0);
        }
        if (eventNewDto.getRequestModeration() == null) {
            event.setRequestModeration(true);
        }
        return EventMapper.INSTANCE.toFullDto(eventRepository.save(event));
    }

    @Transactional
    public EventFullDto updateByAdmin(long eventId, EventUpdateAdminRequest eventUpdateAdminRequest) {
        Event event = findEventById(eventId);
        if (eventUpdateAdminRequest.getEventTimestamp() != null && LocalDateTime.now().plusHours(1).isAfter(eventUpdateAdminRequest.getEventTimestamp())) {
            throw new ConflictException("Дата события должна быть на час позже текущего времени или позднее.");
        }
        if (eventUpdateAdminRequest.getStateAction() != null) {
            if (eventUpdateAdminRequest.getStateAction().equals(EventUpdateAdminRequest.StateAction.PUBLISH_EVENT) &&
                    !event.getState().equals(EventState.PENDING)) {
                throw new ConflictException("Невозможно опубликовать событие, поскольку оно находится в неверном статусе: " + event.getState());
            }
            if (eventUpdateAdminRequest.getStateAction().equals(EventUpdateAdminRequest.StateAction.REJECT_EVENT) &&
                    event.getState().equals(EventState.PUBLISHED)) {
                throw new ConflictException("Невозможно отклонить событие, поскольку оно находится в неверном статусе: " + event.getState());
            }
        }
        if (eventUpdateAdminRequest.getCategory() != null) {
            event.setCategory(findCategoryById(eventUpdateAdminRequest.getCategory()));
        }
        if (eventUpdateAdminRequest.getLocation() != null) {
            event.setLocation(handleLocationDto(eventUpdateAdminRequest.getLocation()));
        }
        Optional.ofNullable(eventUpdateAdminRequest.getTitle()).ifPresent(event::setTitle);
        Optional.ofNullable(eventUpdateAdminRequest.getAnnotation()).ifPresent(event::setAnnotation);
        Optional.ofNullable(eventUpdateAdminRequest.getDescription()).ifPresent(event::setDescription);
        Optional.ofNullable(eventUpdateAdminRequest.getEventTimestamp()).ifPresent(event::setEventDate);
        Optional.ofNullable(eventUpdateAdminRequest.getParticipantLimit()).ifPresent(event::setParticipantLimit);
        Optional.ofNullable(eventUpdateAdminRequest.getPaid()).ifPresent(event::setPaid);
        Optional.ofNullable(eventUpdateAdminRequest.getRequestModeration()).ifPresent(event::setRequestModeration);
        if (eventUpdateAdminRequest.getStateAction() != null) {
            switch (eventUpdateAdminRequest.getStateAction()) {
                case PUBLISH_EVENT:
                    event.setState(EventState.PUBLISHED);
                    event.setPublishedOn(LocalDateTime.now());
                    break;
                case REJECT_EVENT:
                    event.setState(EventState.CANCELED);
                    break;
            }
        }
        event = eventRepository.save(event);
        return EventMapper.INSTANCE.toFullDto(event);
    }

    @Transactional
    public EventFullDto UpdateByInitiator(long userId, long eventId, EventUpdateUserRequest eventUpdateUserRequest) {
        Event event = findEventById(eventId);
        checkInitiator(userId, eventId, event.getInitiator().getId());
        if (eventUpdateUserRequest.getEventTimestamp() != null && LocalDateTime.now().plusHours(2).isAfter(eventUpdateUserRequest.getEventTimestamp())) {
            throw new ConflictException("Дата события должна быть на 2 часа позже текущего времени или позднее.");
        }
        if (!(event.getState().equals(EventState.CANCELED) ||
                event.getState().equals(EventState.PENDING))) {
            throw new ConflictException("Изменить можно только ожидающие или отмененные события.");
        }
        if (eventUpdateUserRequest.getCategory() != null) {
            event.setCategory(findCategoryById(eventUpdateUserRequest.getCategory()));
        }
        if (eventUpdateUserRequest.getLocation() != null) {
            event.setLocation(handleLocationDto(eventUpdateUserRequest.getLocation()));
        }
        Optional.ofNullable(eventUpdateUserRequest.getTitle()).ifPresent(event::setTitle);
        Optional.ofNullable(eventUpdateUserRequest.getAnnotation()).ifPresent(event::setAnnotation);
        Optional.ofNullable(eventUpdateUserRequest.getDescription()).ifPresent(event::setDescription);
        Optional.ofNullable(eventUpdateUserRequest.getEventTimestamp()).ifPresent(event::setEventDate);
        Optional.ofNullable(eventUpdateUserRequest.getParticipantLimit()).ifPresent(event::setParticipantLimit);
        Optional.ofNullable(eventUpdateUserRequest.getPaid()).ifPresent(event::setPaid);
        Optional.ofNullable(eventUpdateUserRequest.getRequestModeration()).ifPresent(event::setRequestModeration);
        if (eventUpdateUserRequest.getStateAction() != null) {
            switch (eventUpdateUserRequest.getStateAction()) {
                case SEND_TO_REVIEW:
                    event.setState(EventState.PENDING);
                    break;
                case CANCEL_REVIEW:
                    event.setState(EventState.CANCELED);
                    break;
            }
        }
        event = eventRepository.save(event);
        return EventMapper.INSTANCE.toFullDto(event);
    }

    @Transactional
    public EventRequestStatusUpdateResult updateParticipationRequestsByInitiator(@PathVariable long userId,
                                                                                 @PathVariable long eventId,
                                                                                 @Valid @RequestBody EventRequestStatusUpdateRequest eventRequestStatusUpdateRequest) {
        findUserById(userId);
        Event event = findEventById(eventId);
        long confirmLimit = event.getParticipantLimit() - participationRequestRepository.countByEventIdAndStatus(eventId, ParticipationRequestState.CONFIRMED);
        if (confirmLimit <= 0) {
            throw new ConflictException("Достигнут лимит участников");
        }
        List<ParticipationRequest> requestList = participationRequestRepository.findAllByIdIn(eventRequestStatusUpdateRequest.getRequestIds());
        List<Long> notFoundIds = eventRequestStatusUpdateRequest.getRequestIds().stream()
                .filter(requestId -> requestList.stream().noneMatch(request -> request.getId().equals(requestId)))
                .collect(Collectors.toList());
        if (!notFoundIds.isEmpty()) {
            throw new NotFoundException("Заявка на участие с id=" + notFoundIds + " не найдена");
        }
        EventRequestStatusUpdateResult result = EventRequestStatusUpdateResult.builder()
                .confirmedRequests(new ArrayList<>())
                .rejectedRequests(new ArrayList<>())
                .build();
        for (ParticipationRequest req : requestList) {
            if (!req.getEvent().getId().equals(eventId)) {
                throw new NotFoundException("Заявка на участие с id=" + req.getId() + " не найдена");
            }
            if (confirmLimit <= 0) {
                req.setStatus(ParticipationRequestState.REJECTED);
                result.getRejectedRequests().add(ParticipationRequestMapper.INSTANCE.toDto(req));
                continue;
            }
            switch (eventRequestStatusUpdateRequest.getStatus()) {
                case CONFIRMED:
                    req.setStatus(ParticipationRequestState.CONFIRMED);
                    result.getConfirmedRequests().add(ParticipationRequestMapper.INSTANCE.toDto(req));
                    confirmLimit--;
                    break;
                case REJECTED:
                    req.setStatus(ParticipationRequestState.REJECTED);
                    result.getRejectedRequests().add(ParticipationRequestMapper.INSTANCE.toDto(req));
                    break;
            }
        }
        participationRequestRepository.saveAll(requestList);
        return result;
    }


    @Transactional(readOnly = true)
    public List<EventFullDto> getAllByAdmin(List<Long> users, List<EventState> states, List<Long> categories,
                                            LocalDateTime rangeStart, LocalDateTime rangeEnd, int from, int size) {
        Pageable pageable = PageRequest.of(from, size);
        if (users != null && users.size() == 1 && users.get(0).equals(0L)) {
            users = null;
        }
        if (categories != null && categories.size() == 1 && categories.get(0).equals(0L)) {
            categories = null;
        }
        if (rangeStart == null) {
            rangeStart = LocalDateTime.now();
        }
        if (rangeEnd == null) {
            rangeEnd = UtilConstants.getMaxDateTime();
        }
        Page<Event> page = eventRepository.findAllByAdmin(users, states, categories, rangeStart, rangeEnd, pageable);
        List<String> eventUrls = page.getContent().stream()
                .map(event -> "/events/" + event.getId())
                .collect(Collectors.toList());
        List<ViewStatsDto> viewStatsDtos = statsClient.getStats(rangeStart.format(UtilConstants.getDefaultDateTimeFormatter()),
                rangeEnd.format(UtilConstants.getDefaultDateTimeFormatter()), eventUrls, true);
        return page.getContent().stream()
                .map(EventMapper.INSTANCE::toFullDto)
                .peek(dto -> {
                    Optional<ViewStatsDto> matchingStats = viewStatsDtos.stream()
                            .filter(statsDto -> statsDto.getUri().equals("/events/" + dto.getId()))
                            .findFirst();
                    dto.setViews(matchingStats.map(ViewStatsDto::getHits).orElse(0L));
                })
                .peek(dto -> dto.setConfirmedRequests(participationRequestRepository.countByEventIdAndStatus(dto.getId(), ParticipationRequestState.CONFIRMED)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventShortDto> getAllByInitiator(long userId, int from, int size) {
        Pageable pageable = PageRequest.of(from, size);
        Page<Event> page = eventRepository.findAllByInitiatorId(userId, pageable);
        return page.getContent().stream()
                .map(EventMapper.INSTANCE::toShortDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EventFullDto getByIdByInitiator(long userId, long eventId) {
        Event event = findEventById(eventId);
        checkInitiator(userId, eventId, event.getInitiator().getId());
        return EventMapper.INSTANCE.toFullDto(event);
    }

    @Transactional(readOnly = true)
    public List<ParticipationRequestDto> getParticipationRequestsByInitiator(long userId, long eventId) {
        findUserById(userId);
        findEventById(eventId);
        return participationRequestRepository.findAllByEventId(eventId).stream()
                .map(ParticipationRequestMapper.INSTANCE::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EventShortDto> getAllPublic(String text, List<Long> categories, Boolean paid, LocalDateTime rangeStart,
                                            LocalDateTime rangeEnd, boolean onlyAvailable, PublicEventController.SortMode sort,
                                            int from, int size, HttpServletRequest request) {
        statsClient.createEndpointHit(EndpointHitDto.builder().app("ewm").uri(request.getRequestURI())
                .ip(request.getRemoteAddr()).hitTimestamp(LocalDateTime.now()).build());
        if (categories != null && categories.size() == 1 && categories.get(0).equals(0L)) {
            categories = null;
        }
        if (rangeStart == null) {
            rangeStart = LocalDateTime.now();
        }
        if (rangeEnd == null) {
            rangeEnd = UtilConstants.getMaxDateTime();
        }
        List<Event> eventList = eventRepository.getAllPublic(text, categories, paid, rangeStart, rangeEnd);
        if (onlyAvailable) {
            eventList = eventList.stream()
                    .filter(event -> event.getParticipantLimit().equals(0)
                            || event.getParticipantLimit() < participationRequestRepository.countByEventIdAndStatus(event.getId(), ParticipationRequestState.CONFIRMED))
                    .collect(Collectors.toList());
        }
        List<String> eventUrls = eventList.stream()
                .map(event -> "/events/" + event.getId())
                .collect(Collectors.toList());
        List<ViewStatsDto> viewStatsDtos = statsClient.getStats(rangeStart.format(UtilConstants.getDefaultDateTimeFormatter()),
                rangeEnd.format(UtilConstants.getDefaultDateTimeFormatter()), eventUrls, true);
        List<EventShortDto> eventShortDtoList = eventList.stream()
                .map(EventMapper.INSTANCE::toShortDto)
                .peek(dto -> {
                    Optional<ViewStatsDto> matchingStats = viewStatsDtos.stream()
                            .filter(statsDto -> statsDto.getUri().equals("/events/" + dto.getId()))
                            .findFirst();
                    dto.setViews(matchingStats.map(ViewStatsDto::getHits).orElse(0L));
                })
                .peek(dto -> dto.setConfirmedRequests(participationRequestRepository.countByEventIdAndStatus(dto.getId(), ParticipationRequestState.CONFIRMED)))
                .collect(Collectors.toList());
        switch (sort) {
            case EVENT_DATE:
                eventShortDtoList.sort(Comparator.comparing(EventShortDto::getEventDate));
                break;
            case VIEWS:
                eventShortDtoList.sort(Comparator.comparing(EventShortDto::getViews).reversed());
                break;
        }
        if (from >= eventShortDtoList.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(from + size, eventShortDtoList.size());
        return eventShortDtoList.subList(from, toIndex);
    }

    @Transactional(readOnly = true)
    public EventFullDto getByIdPublic(long eventId, HttpServletRequest request) {
        Event event = findEventById(eventId);
        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }
        statsClient.createEndpointHit(EndpointHitDto.builder()
                .app("ewm")
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .hitTimestamp(LocalDateTime.now())
                .build());
        List<String> eventUrls = Collections.singletonList("/events/" + event.getId());
        List<ViewStatsDto> viewStatsDtos = statsClient.getStats(UtilConstants.getMinDateTime().format(UtilConstants.getDefaultDateTimeFormatter()),
                UtilConstants.getMaxDateTime().plusYears(1).format(UtilConstants.getDefaultDateTimeFormatter()), eventUrls, true);
        EventFullDto dto = EventMapper.INSTANCE.toFullDto(event);
        dto.setViews(viewStatsDtos.isEmpty() ? 0L : viewStatsDtos.get(0).getHits());
        dto.setConfirmedRequests(participationRequestRepository.countByEventIdAndStatus(dto.getId(), ParticipationRequestState.CONFIRMED));
        return dto;
    }

    private Event findEventById(long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + id + " не найдено"));
    }

    private User findUserById(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + id + " не найден"));
    }

    private Category findCategoryById(long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Категория с id=" + id + " не найдена"));
    }

    private void checkInitiator(long userId, long eventId, long initiatorId) {
        if (userId != initiatorId) {
            throw new NotFoundException("Событие с id=" + eventId + " не найдено");
        }
    }

    private Location handleLocationDto(LocationDto locationDto) {
        Location location = locationRepository.findByLatAndLon(locationDto.getLat(), locationDto.getLon());
        return location != null ? location : locationRepository.save(LocationMapper.INSTANCE.fromDto(locationDto));
    }
}
