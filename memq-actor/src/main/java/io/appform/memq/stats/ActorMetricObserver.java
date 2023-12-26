package io.appform.memq.stats;


import com.codahale.metrics.*;
import io.appform.memq.observer.ActorObserver;
import io.appform.memq.observer.ActorObserverContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

@Slf4j
public class ActorMetricObserver extends ActorObserver {

    private final MetricRegistry metricRegistry;
    private final String actorName;
    private static final String ACTOR_PREFIX = "actor";
    private static final String DELIMITER = ".";
    private static final String DELIMITER_REPLACEMENT = "_";

    @Getter
    private final Map<MetricKeyData, MetricData> metricCache = new ConcurrentHashMap<>();

    public ActorMetricObserver(final String actorName,
                               final LongSupplier sizeSupplier,
                               final MetricRegistry metricRegistry) {
        super(null);
        this.metricRegistry = metricRegistry;
        this.actorName = actorName;
        this.metricRegistry.gauge(MetricRegistry.name(getMetricPrefix(actorName), "size"),
                (MetricRegistry.MetricSupplier<Gauge<Long>>) () ->
                        new CachedGauge<>(1, TimeUnit.SECONDS) {
                            @Override
                            protected Long loadValue() {
                                return sizeSupplier.getAsLong();
                            }
                        });
    }

    @Override
    public Boolean executePublish(final ActorObserverContext context,
                                final BooleanSupplier supplier) {
        val metricData = getMetricData(context);
        metricData.getTotal().mark();
        val timer = metricData.getTimer().time();
        try {
            val response = proceedPublish(context, supplier);
            if (response) {
                metricData.getSuccess().mark();
            } else {
                metricData.getFailed().mark();
            }
            return response;
        } catch (Throwable t) {
            metricData.getFailed().mark();
            throw t;
        } finally {
            timer.stop();
        }
    }

    @Override
    public void executeConsume(ActorObserverContext context,
                               Runnable runnable) {
        metered(context, runnable);
    }

    @Override
    public void executeSideline(ActorObserverContext context, Runnable runnable) {
        metered(context, runnable);
    }

    @Override
    public void executeExceptionHandler(ActorObserverContext context, Runnable runnable) {
        metered(context, runnable);
    }

    private void metered(ActorObserverContext context, Runnable runnable) {
        val metricData = getMetricData(context);
        metricData.getTotal().mark();
        val timer = metricData.getTimer().time();
        try {
            proceedConsume(context, runnable);
            metricData.getSuccess().mark();
        } catch (Throwable t) {
            metricData.getFailed().mark();
            throw t;
        } finally {
            timer.stop();
        }
    }

    private MetricData getMetricData(final ActorObserverContext context) {
        val metricKeyData = MetricKeyData.builder()
                .actorName(actorName)
                .operation(context.getOperation().name())
                .build();
        return metricCache.computeIfAbsent(metricKeyData, key ->
                getMetricData(getMetricPrefix(metricKeyData)));
    }

    private MetricData getMetricData(final String metricPrefix) {
        return MetricData.builder()
                .timer(metricRegistry.timer(MetricRegistry.name(metricPrefix, "latency"),
                        () -> new Timer(new SlidingTimeWindowArrayReservoir(60, TimeUnit.SECONDS))))
                .success(metricRegistry.meter(MetricRegistry.name(metricPrefix, "success")))
                .failed(metricRegistry.meter(MetricRegistry.name(metricPrefix, "failed")))
                .total(metricRegistry.meter(MetricRegistry.name(metricPrefix, "total")))
                .build();
    }

    private String getMetricPrefix(final MetricKeyData metricKeyData) {
        return getMetricPrefix(actorName, metricKeyData.getOperation());
    }

    private String getMetricPrefix(String... metricNames) {
        val metricPrefix = new StringBuilder(ACTOR_PREFIX);
        for (val metricName : metricNames) {
            metricPrefix.append(DELIMITER).append(normalizeString(metricName));
        }
        return metricPrefix.toString();
    }

    private static String normalizeString(final String name) {
        return name.replace(DELIMITER, DELIMITER_REPLACEMENT);
    }
}