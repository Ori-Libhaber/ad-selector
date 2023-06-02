package com.undertone.adselector.infrastructure.out;


import com.undertone.adselector.application.ports.out.AdDistributionStore;
import com.undertone.adselector.model.AdBudgetPlan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.nio.file.Path;

@Configuration
public class ModuleConfiguration {

    @Bean
    public AdBudgetPlan adBudgetPlan(@Value("${plan.file}") Path adBudgetPlanFile,
                                     @Value("${plan.file.watcher.enabled:true}") boolean withFileWatcher) {

        var built = FileBackedAdBudgetPlanStore.builder(adBudgetPlanFile);
        return withFileWatcher ? built.withFileWatcher().build() : built.build();
    }

    @Bean
    public AdDistributionStore adDistributionStore(ReactiveStringRedisTemplate redisTemplate) {
        return new RedisBackedAdDistributionStore(redisTemplate);
    }

}
