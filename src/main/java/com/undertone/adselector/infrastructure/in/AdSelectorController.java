package com.undertone.adselector.infrastructure.in;


import com.undertone.adselector.application.ports.in.SelectAdUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

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
//    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<SelectAdResponse> selectAd(@RequestBody SelectAdRequest request){
        return selectAdUseCase.selectAd(request.q())
                .flatMap(Mono::justOrEmpty)
                    .map(SelectAdResponse::new);
//                        .switchIfEmpty(ResponseEntity.noContent().build());
    }

}
