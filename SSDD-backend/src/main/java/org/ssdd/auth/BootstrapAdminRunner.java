package org.ssdd.auth;

import jakarta.validation.Validator;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

@Component
public class BootstrapAdminRunner implements ApplicationRunner {
    private final BootstrapAdminProperties config;
    private final AuthService auth;
    private final Validator validator;
    public BootstrapAdminRunner(BootstrapAdminProperties config,AuthService auth,Validator validator) { this.config=config;this.auth=auth;this.validator=validator; }
    @Override public void run(ApplicationArguments arguments) {
        if(!config.enabled())return;
        var input=new AuthRequests.Register(config.email(),config.password(),config.displayName());
        if(!validator.validate(input).isEmpty())throw new IllegalStateException("请配置有效的 SSDD_BOOTSTRAP_ADMIN_EMAIL/NAME/PASSWORD");
        boolean created=auth.bootstrap(input);
        LoggerFactory.getLogger(getClass()).info("Administrator bootstrap completed; created={}",created);
    }
}
