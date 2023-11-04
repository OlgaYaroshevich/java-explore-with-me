package ru.practicum.ewm.service.participationRequest.logic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.service.event.models.event.Event;
import ru.practicum.ewm.service.event.models.event.EventRepository;
import ru.practicum.ewm.service.event.models.event.EventState;
import ru.practicum.ewm.service.participationRequest.models.*;
import ru.practicum.ewm.service.user.models.User;
import ru.practicum.ewm.service.user.models.UserRepository;
import ru.practicum.ewm.service.util.exception.ConflictException;
import ru.practicum.ewm.service.util.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParticipationRequestService {
    private final ParticipationRequestRepository participationRequestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    public ParticipationRequestDto create(long userId, long eventId) {
        User requester = findUserById(userId);
        Event event = findEventById(eventId);
       if (event.getInitiator().getId().equals(userId)) {
            throw new ConflictException("Инициатор мероприятия не может подать заявку на участие в собственном мероприятии");
        }
        if (!event.getState().equals(EventState.PUBLISHED)) {
            throw new ConflictException("Невозможно принять участие в неопубликованном мероприятии");
        }
        if (event.getParticipantLimit() > 0) {
            if (event.getParticipantLimit() <= participationRequestRepository.countByEventIdAndStatus(eventId, ParticipationRequestState.CONFIRMED)) {
                throw new ConflictException("Количество заявок на участие превысило лимит для мероприятия");
            }
        }
        ParticipationRequest participationRequest = new ParticipationRequest();
        participationRequest.setRequester(requester);
        participationRequest.setEvent(event);
        participationRequest.setCreated(LocalDateTime.now());
        participationRequest.setStatus(event.getRequestModeration() && !event.getParticipantLimit().equals(0) ? ParticipationRequestState.PENDING : ParticipationRequestState.CONFIRMED);
        return ParticipationRequestMapper.INSTANCE.toDto(participationRequestRepository.save(participationRequest));
    }

    public ParticipationRequestDto update(long userId, long requestId) {
        findUserById(userId);
        ParticipationRequest participationRequest = findParticipationRequestById(requestId);
        if (!participationRequest.getRequester().getId().equals(userId)) {
            throw new NotFoundException("Не найдено событий, доступных для редактирования.");
        }
        participationRequest.setStatus(ParticipationRequestState.CANCELED);
        return ParticipationRequestMapper.INSTANCE.toDto(participationRequestRepository.save(participationRequest));
    }

    public List<ParticipationRequestDto> getAll(long userId) {
        findUserById(userId);

        return participationRequestRepository.findAllByRequesterId(userId).stream()
                .map(ParticipationRequestMapper.INSTANCE::toDto)
                .collect(Collectors.toList());
    }

    private ParticipationRequest findParticipationRequestById(long id) {
        return participationRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Заявка с id=" + id + " не найдена"));
    }

    private User findUserById(long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пользователь с id=" + id + " не найден"));
    }

    private Event findEventById(long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Событие с id=" + id + " не найдено"));
    }
}