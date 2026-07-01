package cn.geelato.security;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class DefaultUserProvider implements UserProvider {
    private final UserSnapshotLoader userSnapshotLoader;
    private final UserOrgInfoEnricher userOrgInfoEnricher;
    private final AtomicReference<UserSnapshot> snapshotRef = new AtomicReference<>(UserSnapshot.empty());

    public DefaultUserProvider(UserSnapshotLoader userSnapshotLoader,
                               UserOrgInfoEnricher userOrgInfoEnricher) {
        this.userSnapshotLoader = userSnapshotLoader;
        this.userOrgInfoEnricher = userOrgInfoEnricher;
    }

    @Override
    public User getUser(String userId) {
        return snapshotRef.get().getUser(userId);
    }

    @Override
    public User getUserByExtendKey(String extendKey, String type) {
        if (extendKey == null || extendKey.isEmpty()) {
            return null;
        }
        return snapshotRef.get().getUserByExtendKey(normalizeType(type), extendKey);
    }

    @Override
    public void refresh() {
        UserSnapshot snapshot = userSnapshotLoader.load(userOrgInfoEnricher);
        snapshotRef.set(snapshot);
        log.info("User provider snapshot refreshed.");
    }
}
