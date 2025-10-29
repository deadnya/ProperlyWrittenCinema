package com.absolute.cinema.service.impl;

import com.absolute.cinema.common.exception.custom.BadRequestException;
import com.absolute.cinema.entity.Media;
import com.absolute.cinema.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class FilmValidator {

    private final MediaService mediaService;

    public boolean validateFilmMediaContent(UUID mediaId) {
        try {
            Media media = mediaService.getMediaById(mediaId);
            return media.getMediaType() == Media.MediaType.IMAGE;
        } catch (Exception e) {
            return false;
        }
    }

    public Media checkMediaIsValidPoster(UUID mediaId) {
        Media media = mediaService.getMediaById(mediaId);
        if (media.getMediaType() != Media.MediaType.IMAGE) {
            throw new BadRequestException("Media is not a valid image file");
        }
        return media;
    }

    public boolean ensureMediaTypeCorrect(UUID mediaId, String expectedType) {
        try {
            Media media = mediaService.getMediaById(mediaId);
            return media.getMediaType().toString().equals(expectedType);
        } catch (Exception e) {
            return false;
        }
    }
}
