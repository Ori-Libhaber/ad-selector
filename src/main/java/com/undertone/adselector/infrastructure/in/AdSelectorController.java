package com.undertone.adselector.infrastructure.in;


import com.undertone.adselector.application.ports.in.SelectAdUseCase;
import com.undertone.adselector.application.ports.in.UseCaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collection;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/")
public class AdSelectorController {

    @Autowired
    private SelectAdUseCase selectAdUseCase;

    @PostMapping(
            path = "selectAd",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public Mono<ResponseEntity<SelectAdResponse>> selectAd(@RequestBody SelectAdRequest request) {
        return selectAdUseCase.selectAd(request.q())
                .map(opSelection -> opSelection
                        .map(SelectAdResponse::new)
                            .map(ResponseEntity::ok)
                                .orElseGet(ResponseEntity.noContent()::build));

    }

    static class NoSelectionException extends ResponseStatusException {
        public NoSelectionException() {
            super(HttpStatus.NO_CONTENT, "No selection could be made for requested population");
        }
    }


}
