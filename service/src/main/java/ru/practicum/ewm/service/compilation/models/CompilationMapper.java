package ru.practicum.ewm.service.compilation.models;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import ru.practicum.ewm.service.event.models.event.Event;
import ru.practicum.ewm.service.event.models.event.EventMapper;

import java.util.List;

@Mapper(uses = EventMapper.class)
public interface CompilationMapper {
    CompilationMapper INSTANCE = Mappers.getMapper(CompilationMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "events", source = "events")
    Compilation fromDto(CompilationNewDto dto, List<Event> events);


    CompilationDto toDto(Compilation entity);
}
