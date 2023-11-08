package ru.practicum.ewm.service.util.errorHandler;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.ewm.service.util.exception.BadRequestException;
import ru.practicum.ewm.service.util.exception.ConflictException;
import ru.practicum.ewm.service.util.exception.ForbiddenException;
import ru.practicum.ewm.service.util.exception.NotFoundException;

import java.time.LocalDateTime;
import java.util.Objects;

@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBindException(BindException e) {
        return buildApiError(HttpStatus.BAD_REQUEST, "Неправильно составлен запросt.",
                "Поле: " + Objects.requireNonNull(e.getFieldError()).getField() + ". Ошибка: " + e.getFieldError().getDefaultMessage() +
                        ". Значение: " + e.getFieldError().getRejectedValue());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMissingParameterException(MissingServletRequestParameterException e) {
        return buildApiError(HttpStatus.BAD_REQUEST, "Неправильно составлен запрос.", e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleCustomBadRequestException(BadRequestException e) {
        return buildApiError(HttpStatus.BAD_REQUEST, "Неправильно составлен запрос.", e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleCustomNotFoundException(NotFoundException e) {
        return buildApiError(HttpStatus.NOT_FOUND, "Нужный объект не найден.", e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleCustomConflictException(ConflictException e) {
        return buildApiError(HttpStatus.CONFLICT, "Для запрошенной операции условия не выполнены.", e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        return buildApiError(HttpStatus.CONFLICT, "Ограничение целостности нарушено.", e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleUnhandledException(Exception e) {
        return buildApiError(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера.",
                e.getClass() + " - " + e.getMessage(), e.getStackTrace());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleForbiddenException(ForbiddenException e) {
        return buildApiError(HttpStatus.FORBIDDEN, "Действие запрещено.",
                e.getMessage());
    }

    private ApiError buildApiError(HttpStatus status, String reason, String message) {
        return ApiError.builder()
                .status(status)
                .reason(reason)
                .message(message)
                .errorTimestamp(LocalDateTime.now())
                .build();
    }

    private ApiError buildApiError(HttpStatus status, String reason, String message, StackTraceElement[] errors) {
        return ApiError.builder()
                .status(status)
                .reason(reason)
                .message(message)
                .errors(errors)
                .errorTimestamp(LocalDateTime.now())
                .build();
    }
}
