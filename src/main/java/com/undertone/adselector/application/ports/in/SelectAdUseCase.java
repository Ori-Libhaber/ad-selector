package com.undertone.adselector.application.ports.in;

import com.undertone.adselector.model.exceptions.ApplicationException;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

public interface SelectAdUseCase {

    public Mono<Optional<String>> selectAd(Set<String> population) throws ApplicationException;

}
