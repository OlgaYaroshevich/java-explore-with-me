package ru.practicum.ewm.service.comment.logic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.ewm.service.comment.models.*;
import ru.practicum.ewm.service.event.models.event.Event;
import ru.practicum.ewm.service.event.models.event.EventRepository;
import ru.practicum.ewm.service.user.models.User;
import ru.practicum.ewm.service.user.models.UserRepository;
import ru.practicum.ewm.service.util.exception.ForbiddenException;
import ru.practicum.ewm.service.util.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Transactional
    public CommentDto create(long userId, long eventId, CommentNewDto dto) {
        User user = findUserById(userId);
        Event event = findEventById(eventId);
        Comment comment = new Comment();
        comment.setUser(user);
        comment.setEvent(event);
        comment.setText(dto.getText());
        comment.setCreatedOn(LocalDateTime.now());
        comment = commentRepository.save(comment);
        return CommentMapper.INSTANCE.toDto(comment);
    }

    @Transactional
    public CommentDto updateByUser(long userId, long commentId, CommentUpdateRequest updateRequest) {
        findUserById(userId);
        Comment comment = findCommentById(commentId);
        if (!comment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Пользователь с id=" + userId + " не владелец комментария с id=" + commentId);
        }
        Optional.ofNullable(updateRequest.getText()).ifPresent(comment::setText);
        comment = commentRepository.save(comment);
        return CommentMapper.INSTANCE.toDto(comment);
    }

    @Transactional
    public CommentDto updateByAdmin(long commentId, CommentUpdateRequest updateRequest) {
        Comment comment = findCommentById(commentId);
        Optional.ofNullable(updateRequest.getText()).ifPresent(comment::setText);
        comment = commentRepository.save(comment);
        return CommentMapper.INSTANCE.toDto(comment);
    }

    @Transactional
    public void deleteByUser(long userId, long commentId) {
        findUserById(userId);
        Comment comment = findCommentById(commentId);
        if (!comment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Пользователь c id=" + userId + " не владелец комментария с id=" + commentId);
        }
        commentRepository.deleteById(commentId);
    }

    @Transactional
    public void deleteByAdmin(long commentId) {
        commentRepository.deleteById(commentId);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> getAllByEventId(long eventId) {
        return commentRepository.findAllByEventId(eventId).stream()
                .map(CommentMapper.INSTANCE::toDto)
                .collect(Collectors.toList());
    }

    private Comment findCommentById(long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Комментарий с id=" + id + " не найден"));
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
