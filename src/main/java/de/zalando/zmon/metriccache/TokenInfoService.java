package de.zalando.zmon.metriccache;

import java.util.Optional;

public interface TokenInfoService {
    Optional<String> lookupUid(String authorizationHeaderValue);
}
