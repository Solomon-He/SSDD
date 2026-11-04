package org.ssdd.inference;

import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.ssdd.model.ModelService;

@Component
public class ModelRecoveryRunner implements ApplicationRunner {
    private final ModelService models;
    public ModelRecoveryRunner(ModelService models) { this.models=models; }
    @Override public void run(ApplicationArguments args) { models.restore(); }
}
