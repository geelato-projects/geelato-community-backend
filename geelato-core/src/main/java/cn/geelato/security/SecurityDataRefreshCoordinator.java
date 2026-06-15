package cn.geelato.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SecurityDataRefreshCoordinator {
    private final OrgProvider orgProvider;
    private final UserProvider userProvider;

    public SecurityDataRefreshCoordinator(OrgProvider orgProvider, @Autowired(required = false) UserProvider userProvider) {
        this.orgProvider = orgProvider;
        this.userProvider = userProvider;
    }

    @PostConstruct
    public void init() {
        refreshAll();
    }

    public synchronized void refreshAll() {
        orgProvider.refresh();
        if (userProvider != null) {
            userProvider.refresh();
        }
        log.info("Security provider snapshots refreshed. userProviderLoaded={}", userProvider != null);
    }

    public synchronized void refreshOrg() {
        orgProvider.refresh();
        if (userProvider != null) {
            userProvider.refresh();
        }
        log.info("Org provider snapshot refreshed with user snapshot cascade. userProviderLoaded={}", userProvider != null);
    }

    public synchronized void refreshUser() {
        if (userProvider != null) {
            userProvider.refresh();
        }
        log.info("User provider snapshot refreshed. userProviderLoaded={}", userProvider != null);
    }
}
