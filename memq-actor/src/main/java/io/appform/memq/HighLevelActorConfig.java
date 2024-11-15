package io.appform.memq;

import io.appform.memq.exceptionhandler.config.DropConfig;
import io.appform.memq.exceptionhandler.config.ExceptionHandlerConfig;
import io.appform.memq.retry.config.NoRetryConfig;
import io.appform.memq.retry.config.RetryConfig;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HighLevelActorConfig {

    @Min(1)
    @Max(100)
    @Builder.Default
    private int partitions = 1;

    @Min(1)
    @Builder.Default
    private long maxSizePerPartition = Long.MAX_VALUE;

    @Min(1)
    @Builder.Default
    private int maxConcurrencyPerPartition = Integer.MAX_VALUE;

    @Valid
    @NotNull
    @Builder.Default
    private RetryConfig retryConfig = new NoRetryConfig();

    @Valid
    @NotNull
    @Builder.Default
    private ExceptionHandlerConfig exceptionHandlerConfig = new DropConfig();

    @NotNull
    @Builder.Default
    private String executorName = "default";

    @Builder.Default
    private boolean metricDisabled = false;

}
