---
title: API · Announcements
sidebar_label: Announcements
---
# Announcements (announcement)

1 controller with 2 endpoints in total. Controllers marked with ⚙ are design-time APIs.

## AnnouncementController — `/announcement`

Login-popup announcement mechanism: announcements are markdown files in a configured directory (`geelato.announcement.dir`, usually maintained by scheduled git pulls) and the endpoint returns the latest one by date; the read state is kept by the frontend in localStorage (`{announcementId}_{userId}_read`) and the server stores nothing.

### current Get the latest announcement

| Item | Content |
| --- | --- |
| URL | `GET /api/announcement/current` |
| Params | None |
| Returns | `ApiResult<AnnouncementInfo>`: `data` is the latest announcement (`id` random-number primary key, i.e. the first segment of the file name; `title` title, the middle segment; `date` announcement date formatted as yyyy-MM-dd; `fileName` announcement file name; `content` markdown source rendered by the frontend); `data` is null when there is no announcement. Each call lightly rescans the directory, so announcements pulled by git take effect without a restart. |

### refresh Force-refresh announcements

| Item | Content |
| --- | --- |
| URL | `POST /api/announcement/refresh` |
| Params | None |
| Returns | `ApiResult<AnnouncementInfo>`: `data` is the refreshed latest announcement (same structure as above, null when none); clears the cache and rescans the directory immediately. Intended for ops scripts after a git pull (`Authorization: SystemToken <fixed key>`, configured at `geelato.security.system-token.token`, overridable via the `GEELATO_SYSTEM_TOKEN` environment variable). |
