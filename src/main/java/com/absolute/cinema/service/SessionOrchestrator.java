package com.absolute.cinema.service;

import com.absolute.cinema.dto.CreateSessionDTO;
import com.absolute.cinema.dto.EditSessionDTO;
import com.absolute.cinema.dto.SessionDTO;
import com.absolute.cinema.dto.SessionPagedListDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SessionOrchestrator {

    private final SessionService sessionService;

    public SessionPagedListDTO getSessions(int page, int size, UUID filmId, Date date) {
        return sessionService.getSessions(page, size, filmId, date);
    }

    public SessionDTO createSession(CreateSessionDTO createSessionDTO) {
        return sessionService.createSession(createSessionDTO);
    }

    public SessionDTO getSession(UUID id) {
        return sessionService.getSession(id);
    }

    public SessionDTO editSession(UUID id, EditSessionDTO editSessionDTO) {
        return sessionService.editSession(id, editSessionDTO);
    }

    public void deleteSession(UUID id) {
        sessionService.deleteSession(id);
    }
}
