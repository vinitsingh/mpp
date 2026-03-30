package dev.mpp.protocol.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenRequest {
    private String realm;
    private String method;
    private String intent;
    private String requestB64;
    private String expires;
}
