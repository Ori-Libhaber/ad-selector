package com.undertone.adselector.infrastructure.out;


import com.undertone.adselector.application.ports.out.AdBudgetPlanStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class ModuleConfiguration {

    @Bean
    public AdBudgetPlanStore adBudgetPlan(@Value("${plan.file}") Path adBudgetPlanFile,
                                          @Value("${plan.file.watcher.enabled:true}") boolean enableFileWatcher,
                                          @Value("${plan.file.lazy.loading.enabled:false}") boolean enableLazyLoading) {

        return FileBackedAdBudgetPlanStore
                .builder(adBudgetPlanFile).withFileWatcher(enableFileWatcher)
                    .withLazyLoading(enableLazyLoading)
                        .build();
    }

}
