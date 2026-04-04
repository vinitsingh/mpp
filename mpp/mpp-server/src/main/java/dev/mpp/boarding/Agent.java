package dev.mpp.boarding;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Agent {

    private String agentId;
    private String name;
    private String owner;
    private Instant createdAt;

    @Builder.Default
    private boolean active = true;
}
