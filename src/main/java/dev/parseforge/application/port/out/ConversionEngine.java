package dev.parseforge.application.port.out;

import dev.parseforge.domain.model.ConversionRequest;
import dev.parseforge.domain.model.ConversionResult;
import dev.parseforge.domain.model.EngineDescriptor;
import dev.parseforge.domain.model.EngineState;

public interface ConversionEngine {
    EngineDescriptor descriptor();

    EngineState state();

    ConversionResult convert(ConversionRequest request, ConversionEventListener listener);

    void cancel();
}
