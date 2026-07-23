package cn.geelato.web.platform.srv.email.service;

import cn.geelato.meta.User;
import cn.geelato.meta.UserEmailAccount;
import cn.geelato.meta.email.UserEmailContact;
import cn.geelato.orm.query.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.query.Order;
import cn.geelato.web.platform.srv.email.dto.EmailAddressDto;
import cn.geelato.web.platform.srv.email.dto.EmailContactBackfillRequest;
import cn.geelato.web.platform.srv.email.dto.EmailContactDto;
import cn.geelato.web.platform.srv.email.dto.EmailContactUpsertRequest;
import cn.geelato.web.platform.srv.email.dto.EmailRecipientSuggestionDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
public class EmailContactService {
    private static final int DEFAULT_LIMIT = 20;

    @Autowired
    private EmailInboxService emailInboxService;

    public record PageResult<T>(List<T> data, long total) {
    }

    public record BackfillResult(int createdCount, int updatedCount, int skippedCount) {
    }

    public PageResult<EmailContactDto> pageQuery(String userId, String keyword, Integer favoriteFlag, String sourceType,
                                                 String emailAccountId, int pageNum, int pageSize) {
        List<EmailContactDto> all = listContacts(userId, favoriteFlag, sourceType, emailAccountId, keyword);
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.max(1, pageSize);
        int offset = (safePageNum - 1) * safePageSize;
        if (offset >= all.size()) {
            return new PageResult<>(Collections.emptyList(), all.size());
        }
        int to = Math.min(offset + safePageSize, all.size());
        return new PageResult<>(all.subList(offset, to), all.size());
    }

    public EmailContactDto get(String userId, String id) {
        Map<String, Object> row = MetaFactory.query(UserEmailContact.class)
                .where(
                        Filter.eq("id", id),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .one();
        return row == null || row.isEmpty() ? null : toDto(row);
    }

    public String createOrUpdate(String userId, String tenantCode, EmailContactUpsertRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        String email = normalizeEmail(req.getEmailAddress());
        if (Strings.isBlank(email)) {
            throw new IllegalArgumentException("联系人邮箱不能为空");
        }
        Integer favoriteFlag = req.getFavoriteFlag() != null ? req.getFavoriteFlag() : 0;
        Integer enableStatus = req.getEnableStatus() != null ? req.getEnableStatus() : 1;
        String sourceType = Strings.isNotBlank(req.getSourceType()) ? req.getSourceType() : "manual";
        String name = trimToNull(req.getName());
        String companyName = trimToNull(req.getCompanyName());
        String remark = trimToNull(req.getRemark());
        String tagsJson = trimToNull(req.getTagsJson());
        String emailAccountId = trimToNull(req.getEmailAccountId());

        if (Strings.isBlank(req.getId())) {
            Map<String, Object> exists = findByEmail(userId, email);
            if (exists != null && !exists.isEmpty()) {
                String id = Objects.toString(exists.get("id"), null);
                MetaFactory.update(UserEmailContact.class)
                        .where(
                                Filter.eq("id", id),
                                Filter.eq("userId", userId),
                                Filter.eq("delStatus", 0)
                        )
                        .value("emailAccountId", emailAccountId)
                        .value("name", name)
                        .value("companyName", companyName)
                        .value("remark", remark)
                        .value("tagsJson", tagsJson)
                        .value("favoriteFlag", favoriteFlag)
                        .value("sourceType", sourceType)
                        .value("enableStatus", enableStatus)
                        .save();
                return id;
            }
            return MetaFactory.insert(UserEmailContact.class)
                    .value("tenantCode", tenantCode)
                    .value("userId", userId)
                    .value("emailAccountId", emailAccountId)
                    .value("name", name)
                    .value("emailAddress", email)
                    .value("companyName", companyName)
                    .value("remark", remark)
                    .value("tagsJson", tagsJson)
                    .value("favoriteFlag", favoriteFlag)
                    .value("sourceType", sourceType)
                    .value("enableStatus", enableStatus)
                    .save();
        }

        Map<String, Object> exists = MetaFactory.query(UserEmailContact.class)
                .where(
                        Filter.eq("id", req.getId()),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .one();
        if (exists == null || exists.isEmpty()) {
            throw new IllegalArgumentException("联系人不存在或无权限");
        }
        MetaFactory.update(UserEmailContact.class)
                .where(
                        Filter.eq("id", req.getId()),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .value("emailAccountId", emailAccountId)
                .value("name", name)
                .value("emailAddress", email)
                .value("companyName", companyName)
                .value("remark", remark)
                .value("tagsJson", tagsJson)
                .value("favoriteFlag", favoriteFlag)
                .value("sourceType", sourceType)
                .value("enableStatus", enableStatus)
                .save();
        return req.getId();
    }

    public boolean remove(String userId, String id) {
        MetaFactory.update(UserEmailContact.class)
                .where(
                        Filter.eq("id", id),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .value("delStatus", 1)
                .value("deleteAt", new Date())
                .save();
        return true;
    }

    public boolean setFavorite(String userId, String id, boolean favoriteFlag) {
        MetaFactory.update(UserEmailContact.class)
                .where(
                        Filter.eq("id", id),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .value("favoriteFlag", favoriteFlag ? 1 : 0)
                .save();
        return true;
    }

    public List<EmailRecipientSuggestionDto> listFavorites(String userId, String keyword, String emailAccountId, int limit) {
        return toSuggestions(listContacts(userId, 1, null, emailAccountId, keyword), limit);
    }

    public List<EmailRecipientSuggestionDto> listRecent(String userId, String emailAccountId, int limit) {
        List<EmailContactDto> all = listContacts(userId, null, null, emailAccountId, null);
        all.sort(Comparator
                .comparing((EmailContactDto it) -> it.getLastContactAt() != null ? it.getLastContactAt().getTime() : Long.MIN_VALUE)
                .reversed()
                .thenComparing(it -> it.getFavoriteFlag() != null ? it.getFavoriteFlag() : 0, Comparator.reverseOrder()));
        return toSuggestions(all, limit);
    }

    public List<EmailRecipientSuggestionDto> suggest(String userId, String keyword, String emailAccountId, int limit) {
        if (Strings.isBlank(keyword)) {
            return listFavorites(userId, null, emailAccountId, limit);
        }
        List<EmailContactDto> all = listContacts(userId, null, null, emailAccountId, keyword);
        all.sort(Comparator
                .comparing((EmailContactDto it) -> it.getFavoriteFlag() != null ? it.getFavoriteFlag() : 0, Comparator.reverseOrder())
                .thenComparing((EmailContactDto it) -> matchScore(it, keyword), Comparator.reverseOrder())
                .thenComparing((EmailContactDto it) -> it.getLastContactAt() != null ? it.getLastContactAt().getTime() : Long.MIN_VALUE, Comparator.reverseOrder()));
        return toSuggestions(all, limit);
    }

    public BackfillResult backfill(String userId, String tenantCode, EmailContactBackfillRequest req) throws Exception {
        EmailContactBackfillRequest request = req != null ? req : new EmailContactBackfillRequest();
        List<String> folders = request.getFolders() == null || request.getFolders().isEmpty()
                ? List.of("INBOX", "Sent")
                : request.getFolders();
        int messageLimit = request.getMessageLimit() != null && request.getMessageLimit() > 0 ? request.getMessageLimit() : 200;
        boolean includeTo = request.getIncludeTo() == null || request.getIncludeTo();
        boolean includeCc = request.getIncludeCc() == null || request.getIncludeCc();
        boolean includeFrom = request.getIncludeFrom() == null || request.getIncludeFrom();
        Set<String> excludedEmails = buildExcludedEmails(userId, tenantCode);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        List<EmailInboxService.ContactAddressRecord> records = emailInboxService.collectContactAddresses(
                userId,
                request.getEmailAccountId(),
                folders,
                messageLimit,
                includeTo,
                includeCc,
                includeFrom
        );
        for (EmailInboxService.ContactAddressRecord record : records) {
            EmailAddressDto address = record.address();
            String normalized = normalizeEmail(address != null ? address.getAddress() : null);
            if (Strings.isBlank(normalized) || excludedEmails.contains(normalized)) {
                skipped++;
                continue;
            }
            boolean exists = upsertContactFromInteraction(
                    userId,
                    tenantCode,
                    request.getEmailAccountId(),
                    address,
                    "inbox_backfill",
                    record.sentAt(),
                    record.receivedAt()
            );
            if (exists) {
                updated++;
            } else {
                created++;
            }
        }
        log.debug("emailContact backfill done, userId={}, emailAccountId={}, created={}, updated={}, skipped={}",
                userId, request.getEmailAccountId(), created, updated, skipped);
        return new BackfillResult(created, updated, skipped);
    }

    public void touchSentContacts(String userId, String tenantCode, String emailAccountId, List<EmailAddressDto> addresses) {
        if (addresses == null || addresses.isEmpty()) {
            return;
        }
        Set<String> excludedEmails = buildExcludedEmails(userId, tenantCode);
        Date now = new Date();
        for (EmailAddressDto address : addresses) {
            String normalized = normalizeEmail(address != null ? address.getAddress() : null);
            if (Strings.isBlank(normalized) || excludedEmails.contains(normalized)) {
                continue;
            }
            upsertContactFromInteraction(userId, tenantCode, emailAccountId, address, "send", now, null);
        }
    }

    private List<EmailContactDto> listContacts(String userId, Integer favoriteFlag, String sourceType, String emailAccountId, String keyword) {
        var query = MetaFactory.query(UserEmailContact.class)
                .where(Filter.eq("userId", userId), Filter.eq("delStatus", 0))
                .order(Order.desc("favoriteFlag"), Order.desc("lastContactAt"), Order.desc("updateAt"));
        if (favoriteFlag != null) {
            query.where(Filter.eq("userId", userId), Filter.eq("delStatus", 0), Filter.eq("favoriteFlag", favoriteFlag));
        }
        if (Strings.isNotBlank(sourceType)) {
            query.where(Filter.eq("userId", userId), Filter.eq("delStatus", 0),
                    favoriteFlag != null ? Filter.eq("favoriteFlag", favoriteFlag) : null,
                    Filter.eq("sourceType", sourceType));
        }
        List<Map<String, Object>> rows = MetaFactory.query(UserEmailContact.class)
                .where(buildFilters(userId, favoriteFlag, sourceType, emailAccountId))
                .order(Order.desc("favoriteFlag"), Order.desc("lastContactAt"), Order.desc("updateAt"))
                .list();
        List<EmailContactDto> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            EmailContactDto dto = toDto(row);
            if (matchesKeyword(dto, keyword)) {
                list.add(dto);
            }
        }
        return list;
    }

    private Filter[] buildFilters(String userId, Integer favoriteFlag, String sourceType, String emailAccountId) {
        List<Filter> filters = new ArrayList<>();
        filters.add(Filter.eq("userId", userId));
        filters.add(Filter.eq("delStatus", 0));
        if (favoriteFlag != null) {
            filters.add(Filter.eq("favoriteFlag", favoriteFlag));
        }
        if (Strings.isNotBlank(sourceType)) {
            filters.add(Filter.eq("sourceType", sourceType));
        }
        if (Strings.isNotBlank(emailAccountId)) {
            filters.add(Filter.eq("emailAccountId", emailAccountId));
        }
        return filters.toArray(new Filter[0]);
    }

    private boolean upsertContactFromInteraction(String userId, String tenantCode, String emailAccountId, EmailAddressDto address,
                                                 String sourceType, Date lastSentAt, Date lastReceivedAt) {
        String email = normalizeEmail(address != null ? address.getAddress() : null);
        if (Strings.isBlank(email)) {
            return false;
        }
        Map<String, Object> exists = findByEmail(userId, email);
        Date lastContactAt = maxDate(lastSentAt, lastReceivedAt);
        if (exists != null && !exists.isEmpty()) {
            Date mergedSentAt = maxDate(dateVal(exists.get("lastSentAt")), lastSentAt);
            Date mergedReceivedAt = maxDate(dateVal(exists.get("lastReceivedAt")), lastReceivedAt);
            Date mergedContactAt = maxDate(maxDate(dateVal(exists.get("lastContactAt")), lastContactAt), maxDate(mergedSentAt, mergedReceivedAt));
            int contactCount = intVal(exists.get("contactCount")) + 1;
            MetaFactory.update(UserEmailContact.class)
                    .where(
                            Filter.eq("id", exists.get("id")),
                            Filter.eq("userId", userId),
                            Filter.eq("delStatus", 0)
                    )
                    .value("emailAccountId", Strings.isNotBlank(emailAccountId) ? emailAccountId : exists.get("emailAccountId"))
                    .value("name", chooseName(address != null ? address.getName() : null, exists.get("name")))
                    .value("sourceType", Objects.toString(exists.get("sourceType"), sourceType))
                    .value("lastSentAt", mergedSentAt)
                    .value("lastReceivedAt", mergedReceivedAt)
                    .value("lastContactAt", mergedContactAt)
                    .value("contactCount", Math.max(1, contactCount))
                    .save();
            return true;
        }

        MetaFactory.insert(UserEmailContact.class)
                .value("tenantCode", tenantCode)
                .value("userId", userId)
                .value("emailAccountId", emailAccountId)
                .value("name", trimToNull(address != null ? address.getName() : null))
                .value("emailAddress", email)
                .value("favoriteFlag", 0)
                .value("sourceType", sourceType)
                .value("lastSentAt", lastSentAt)
                .value("lastReceivedAt", lastReceivedAt)
                .value("lastContactAt", lastContactAt)
                .value("contactCount", 1)
                .value("enableStatus", 1)
                .save();
        return false;
    }

    private Set<String> buildExcludedEmails(String userId, String tenantCode) {
        Set<String> set = new HashSet<>();
        List<Map<String, Object>> accountRows = MetaFactory.query(UserEmailAccount.class)
                .where(Filter.eq("userId", userId), Filter.eq("delStatus", 0))
                .list();
        for (Map<String, Object> row : accountRows) {
            String email = normalizeEmail(Objects.toString(row.get("emailAddress"), null));
            if (Strings.isNotBlank(email)) {
                set.add(email);
            }
        }
        List<Filter> userFilters = new ArrayList<>();
        userFilters.add(Filter.eq("delStatus", 0));
        userFilters.add(Filter.eq("enableStatus", 1));
        if (Strings.isNotBlank(tenantCode)) {
            userFilters.add(Filter.eq("tenantCode", tenantCode));
        }
        List<Map<String, Object>> userRows = MetaFactory.query(User.class)
                .where(userFilters.toArray(new Filter[0]))
                .list();
        for (Map<String, Object> row : userRows) {
            String email = normalizeEmail(Objects.toString(row.get("email"), null));
            if (Strings.isNotBlank(email)) {
                set.add(email);
            }
        }
        return set;
    }

    private List<EmailRecipientSuggestionDto> toSuggestions(List<EmailContactDto> contacts, int limit) {
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        List<EmailRecipientSuggestionDto> list = new ArrayList<>(Math.min(safeLimit, contacts.size()));
        for (int i = 0; i < contacts.size() && i < safeLimit; i++) {
            EmailContactDto contact = contacts.get(i);
            EmailRecipientSuggestionDto dto = new EmailRecipientSuggestionDto();
            dto.setId(contact.getId());
            dto.setName(contact.getName());
            dto.setEmailAddress(contact.getEmailAddress());
            dto.setFavoriteFlag(contact.getFavoriteFlag());
            dto.setLastContactAt(contact.getLastContactAt());
            dto.setSourceType(contact.getSourceType());
            list.add(dto);
        }
        return list;
    }

    private EmailContactDto toDto(Map<String, Object> row) {
        EmailContactDto dto = new EmailContactDto();
        dto.setId(Objects.toString(row.get("id"), null));
        dto.setEmailAccountId(Objects.toString(row.get("emailAccountId"), null));
        dto.setName(Objects.toString(row.get("name"), null));
        dto.setEmailAddress(Objects.toString(row.get("emailAddress"), null));
        dto.setCompanyName(Objects.toString(row.get("companyName"), null));
        dto.setRemark(Objects.toString(row.get("remark"), null));
        dto.setTagsJson(Objects.toString(row.get("tagsJson"), null));
        dto.setFavoriteFlag(intVal(row.get("favoriteFlag")));
        dto.setSourceType(Objects.toString(row.get("sourceType"), null));
        dto.setLastSentAt(dateVal(row.get("lastSentAt")));
        dto.setLastReceivedAt(dateVal(row.get("lastReceivedAt")));
        dto.setLastContactAt(dateVal(row.get("lastContactAt")));
        dto.setContactCount(intVal(row.get("contactCount")));
        dto.setEnableStatus(intVal(row.get("enableStatus")));
        dto.setCreateAt(dateVal(row.get("createAt")));
        dto.setUpdateAt(dateVal(row.get("updateAt")));
        return dto;
    }

    private Map<String, Object> findByEmail(String userId, String emailAddress) {
        return MetaFactory.query(UserEmailContact.class)
                .where(
                        Filter.eq("userId", userId),
                        Filter.eq("emailAddress", emailAddress),
                        Filter.eq("delStatus", 0)
                )
                .one();
    }

    private boolean matchesKeyword(EmailContactDto dto, String keyword) {
        if (Strings.isBlank(keyword)) {
            return true;
        }
        String lower = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(dto.getName(), lower)
                || contains(dto.getEmailAddress(), lower)
                || contains(dto.getCompanyName(), lower)
                || contains(dto.getRemark(), lower);
    }

    private int matchScore(EmailContactDto dto, String keyword) {
        String lower = keyword.trim().toLowerCase(Locale.ROOT);
        if (contains(dto.getEmailAddress(), lower)) {
            return 3;
        }
        if (contains(dto.getName(), lower)) {
            return 2;
        }
        if (contains(dto.getCompanyName(), lower) || contains(dto.getRemark(), lower)) {
            return 1;
        }
        return 0;
    }

    private boolean contains(String raw, String keyword) {
        return raw != null && raw.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private String normalizeEmail(String raw) {
        return Strings.isBlank(raw) ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String chooseName(String newName, Object oldName) {
        String candidate = trimToNull(newName);
        return Strings.isNotBlank(candidate) ? candidate : Objects.toString(oldName, null);
    }

    private Integer intVal(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception ex) {
            return 0;
        }
    }

    private Date dateVal(Object o) {
        return o instanceof Date d ? d : null;
    }

    private Date maxDate(Date a, Date b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.after(b) ? a : b;
    }
}
